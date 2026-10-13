package com.zrlog.admin.business.service;

import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class AdminAuditAgentDatabaseTest {
    @Test public void persistsCallerIdentityAndPreservesItAcrossLaterWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminAuditService service = new AdminAuditService();
            service.record(request("zrlogctl/0.1.42 dot/1.2-beta.1"), AdminAuditAction.REFRESH_CACHE);
            service.record(request("zrlogctl/0.1.42 muse"), AdminAuditAction.REFRESH_CACHE);
            service.record(request("zrlogctl/0.1.42"), AdminAuditAction.REFRESH_CACHE);
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
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHeader")) return "User-Agent".equals(args[0]) ? userAgent : null;
                    if (method.getName().equals("getUri")) return "/api/admin/cache";
                    if (method.getName().equals("getRemoteAddr")) return "127.0.0.1";
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    return null;
                });
    }
}
