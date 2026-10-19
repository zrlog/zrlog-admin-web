package com.zrlog.admin.web.controller.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hibegin.common.dao.DAO;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.DevInfoResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.util.DistributedLockManager;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.WebSite;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

public class AdminDevControllerTest {

    private ZrLogConfig applicationConfig;

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
    public void shouldProvideReadOnlyUiReviewWithoutChangingDevelopmentMode() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String runMode = System.getProperty("sws.run.mode");
            Object before = db.queryOne("select value from website where name=?", "admin_audit_log");
            AdminPageDataResponse<Boolean> result = controller(Map.of()).ui();
            assertEquals(Boolean.TRUE, result.getData());
            assertEquals(runMode, System.getProperty("sws.run.mode"));
            assertEquals(before, db.queryOne("select value from website where name=?", "admin_audit_log"));
            assertEquals(AccountAction.SYSTEM_MANAGE, AdminDevController.class.getMethod("ui")
                    .getAnnotation(RequiresAction.class).value());
            assertEquals(HttpMethod.GET, AdminDevController.class.getMethod("ui")
                    .getAnnotation(RequestMethod.class).method());
        }
    }

    @Test
    public void shouldLoadDevInfoFromRealCacheStore() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
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
            applicationConfig = db.config();
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
            applicationConfig = ignored.config();
            ServerConfig config = new ServerConfig();
            var resources =
                    new AdminResourceService(
                            AdminResourceInfoResponse.Capabilities::new,
                            new FeatureLabService(
                                    () -> true,
                                    new WebhookService(
                                            ApplicationServicesFixture.messages(),
                                            ApplicationServicesFixture.websiteCache()),
                                    WebSite::new),
                            ApplicationServicesFixture.site(ignored.config()),
                            () ->
                                    new WebSite()
                                            .getStringValueByName("admin_static_resource_base_url"),
                            () -> new UserPasskey(DAO.getDefaultDataSource()));
            assertFalse(resources.adminResourceInfo(request(Map.of(), config)).isDevMode());
            ApiStandardResponse<Boolean> enabled = controller(Map.of("enabled", "true")).mode();

            assertEquals(Boolean.TRUE, enabled.getData());
            assertEquals("dev", System.getProperty("sws.run.mode"));
            assertTrue(resources.adminResourceInfo(request(Map.of(), config)).isDevMode());
            controller(Map.of("enabled", "false"), config).mode();
            assertFalse(resources.adminResourceInfo(request(Map.of(), config)).isDevMode());
        }
    }

    @Test
    public void shouldReleaseDevLocksAndRecordAudit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            assertEquals(0, controller(Map.of()).releaseLocks().getError());

            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("RELEASE_DEV_LOCKS"));
        }
    }

    private AdminDevController controller(Map<String, String> params) throws Exception {
        return controller(params, new ServerConfig());
    }

    private AdminDevController controller(Map<String, String> params, ServerConfig serverConfig)
            throws Exception {
        AdminDevController controller =
                new AdminDevController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(),
                                AccountServicesFixture.component().permissions()),
                        ApplicationServicesFixture.audit(),
                        ApplicationServicesFixture.websiteCache(),
                        new DistributedLockManager(
                                WebSite::new, ApplicationServicesFixture.locks()));
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
