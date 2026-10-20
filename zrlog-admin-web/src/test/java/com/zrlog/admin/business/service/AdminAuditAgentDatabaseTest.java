package com.zrlog.admin.business.service;

import com.zrlog.admin.web.audit.AdminAuditContexts;
import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdminAuditAgentDatabaseTest {
    @Test
    public void capturedAuditSourceSurvivesRequestChangesAndWorkerDispatch() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            var service = new AdminAuditService(ApplicationServicesFixture.websiteKv(db.dataSource()));
            Map<String, String> source = new HashMap<>(Map.of(
                    "User-Agent", "zrlogctl/0.1.42 dot/1.2-beta.1",
                    "uri", "/mcp", "ip", "192.0.2.10"));
            var captured = AdminAuditContexts.from(request(source), 2);
            // A later session and a reused request cannot change the completed action's attribution.
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            source.put("User-Agent", "another-client/9");
            source.put("uri", "/api/admin/cache");
            source.put("ip", "192.0.2.20");
            service.record(captured, AdminAuditAction.CREATE_ARTICLE, "captured article");
            var worker = Executors.newSingleThreadExecutor();
            try {
                worker.submit(() -> {
                    assertNull(AdminTokenThreadLocal.getUser());
                    service.record(captured, AdminAuditAction.UPDATE_PASSWORD, "secret must be removed");
                }).get(10, TimeUnit.SECONDS);
            } finally {
                worker.shutdownNow();
            }
            var logs = service.getRecentLogs();
            assertEquals(2, logs.size());
            for (var log : logs) {
                assertEquals(Integer.valueOf(2), log.getActorUserId());
                assertEquals("192.0.2.10", log.getIp());
                assertEquals("MCP", log.getBrowser());
                assertEquals("dot", log.getAgentName());
                assertEquals("1.2-beta.1", log.getAgentVersion());
            }
            assertEquals("", logs.get(0).getContent());
            assertEquals("captured article", logs.get(1).getContent());
        }
    }

    @Test public void persistsCallerIdentityAndPreservesItAcrossLaterWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminAuditService service =
                    new AdminAuditService(ApplicationServicesFixture.websiteKv(db.dataSource()));
            service.record(AdminAuditContexts.from(request("zrlogctl/0.1.42 dot/1.2-beta.1"), 1), AdminAuditAction.REFRESH_CACHE);
            service.record(AdminAuditContexts.from(request("zrlogctl/0.1.42 muse"), 1), AdminAuditAction.REFRESH_CACHE);
            service.record(AdminAuditContexts.from(request("zrlogctl/0.1.42"), 1), AdminAuditAction.REFRESH_CACHE);
            var stored = JsonParser.parseString(db.scalar("select value from website where name='admin_audit_log'").toString()).getAsJsonArray();
            assertEquals(3, stored.size());
            assertEquals("dot", stored.get(2).getAsJsonObject().get("agentName").getAsString());
            assertEquals("1.2-beta.1", stored.get(2).getAsJsonObject().get("agentVersion").getAsString());
            var logs = service.getRecentLogs();
            assertNull(logs.get(0).getAgentName());
            assertEquals("muse", logs.get(1).getAgentName());
            assertNull(logs.get(1).getAgentVersion());
            assertEquals("dot", logs.get(2).getAgentName());
            assertEquals("1.2-beta.1", logs.get(2).getAgentVersion());
            assertEquals("ZrLogCtl 0.1.42", logs.get(2).getBrowser());
            assertEquals(Integer.valueOf(1), logs.get(2).getActorUserId());
        }
    }

    private static HttpRequest request(String userAgent) {
        return request(Map.of("User-Agent", userAgent, "uri", "/api/admin/cache", "ip", "127.0.0.1"));
    }

    private static HttpRequest request(Map<String, String> source) {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeader")) return source.get(args[0]);
                    if (method.getName().equals("getUri")) return source.get("uri");
                    if (method.getName().equals("getRemoteHost")) return source.get("ip");
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    return null;
                });
    }
}
