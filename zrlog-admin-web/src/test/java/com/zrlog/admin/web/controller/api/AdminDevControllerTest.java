package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DevInfoResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.rest.response.ApiStandardResponse;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AdminDevControllerTest {

    private final String previousRunMode = System.getProperty("sws.run.mode");

    @After
    public void tearDown() {
        if (previousRunMode == null) {
            System.clearProperty("sws.run.mode");
        } else {
            System.setProperty("sws.run.mode", previousRunMode);
        }
    }

    @Test
    public void shouldLoadDevInfoFromRealCacheStore() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminDevController controller = controller(Map.of());

            AdminPageDataResponse<DevInfoResponse> response = controller.index();

            assertNotNull(response.getData().getLocks());
            assertNotNull(response.getData().getCacheEntries());
            assertFalse(response.getData().isDevMode());
        }
    }

    @Test
    public void shouldEnableAndDisableDevModeWithAuditRecord() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            ServerConfig serverConfig = new ServerConfig();
            AdminDevController enable = controller(Map.of(), serverConfig);

            enable.enable();
            ApiStandardResponse<Boolean> disabled = controller(Map.of("enabled", "false"), serverConfig).mode();

            assertFalse(disabled.getData());
            String audit = String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value"));
            assertTrue(audit.contains("ENABLE_DEV_MODE"));
            assertTrue(audit.contains("DISABLE_DEV_MODE"));
        }
    }

    @Test
    public void shouldSetDevModeThroughModeEndpoint() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ApiStandardResponse<Boolean> enabled = controller(Map.of("enabled", "true")).mode();

            assertEquals(Boolean.TRUE, enabled.getData());
            assertEquals("dev", System.getProperty("sws.run.mode"));
        }
    }

    @Test
    public void shouldReleaseDevLocksAndRecordAudit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            assertEquals(0, controller(Map.of()).releaseLocks().getError());

            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("RELEASE_DEV_LOCKS"));
        }
    }

    private static AdminDevController controller(Map<String, String> params) throws Exception {
        return controller(params, new ServerConfig());
    }

    private static AdminDevController controller(Map<String, String> params, ServerConfig serverConfig)
            throws Exception {
        AdminDevController controller = new AdminDevController();
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(params, serverConfig));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response());
        return controller;
    }

    private static HttpRequest request(Map<String, String> params, ServerConfig serverConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminDevControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return "/api/admin/dev";
                        case "getParaToBool":
                            return Boolean.parseBoolean(params.getOrDefault(args[0].toString(), args[1].toString()));
                        case "getHeader":
                            return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getServerConfig":
                            return serverConfig;
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                AdminDevControllerTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }
}
