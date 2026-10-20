package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminStaticSiteSyncResponse;
import com.zrlog.admin.business.rest.response.SystemResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.RuntimeInfoFixture;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.BlogBuildInfoUtil;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.*;

public class AdminSystemAndStaticSiteControllerTest {

    private static void setControllerFields(Controller controller, HttpRequest request, HttpResponse response)
            throws Exception {
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request);
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response);
    }

    private static HttpRequest request(String uri, Map<String, String> headers) {
        ServerConfig serverConfig = new ServerConfig()
                .setApplicationName("zrlog-admin-test")
                .setServerInfo("zrlog-admin-test/1.0");
        return (HttpRequest) Proxy.newProxyInstance(
                AdminSystemAndStaticSiteControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return uri;
                        case "getHeader":
                            if (headers.containsKey(args[0].toString())) {
                                return headers.get(args[0].toString());
                            }
                            if ("Host".equals(args[0])) {
                                return "localhost:18080";
                            }
                            return "User-Agent".equals(args[0]) ? "JUnit" : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getContextPath":
                            return "/";
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
                AdminSystemAndStaticSiteControllerTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    @Test
    public void shouldReturnSystemPageDataWithServerInfo() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminSystemController controller =
                    new AdminSystemController(
                            RuntimeInfoFixture.from(ignored.config()),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(ignored.config()),
                                    ApplicationServicesFixture.buildId(ignored.resources()),
                                    ApplicationServicesFixture.messages(ignored.dataSource())),
                            AccountServicesFixture.component(ignored.dataSource()).requestIdentity());
            setControllerFields(controller, request("/api/admin/system", Map.of()), response());

            AdminPageDataResponse<SystemResponse> response = controller.index();

            assertNotNull(response.getData());
            assertFalse(response.getData().getServerInfos().isEmpty());
            assertFalse(response.getData().getServerInfos2().isEmpty());
            assertNotNull(response.getData().getDockerMode());
            assertNotNull(response.getData().getNativeImageMode());
            assertEquals(BlogBuildInfoUtil.getVersion(), response.getData().getServerInfos().stream()
                    .filter(item -> "programInfo".equals(item.getKey())).findFirst().orElseThrow().getValue());
            assertEquals(BlogBuildInfoUtil.getBuildId(), response.getData().getServerInfos().stream()
                    .filter(item -> "buildInfo".equals(item.getKey())).findFirst().orElseThrow().getValue());
        }
    }

    @Test
    public void shouldReportAdminStaticSiteSyncedWhenStaticSiteIsDisabled() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminStaticSiteController controller =
                    new AdminStaticSiteController(
                            ApplicationServicesFixture.cacheRefresh(null, ignored.config()),
                            ApplicationServicesFixture.publisher(null, ignored.config()),
                            () -> ignored.config().getPlugin(AdminStaticResourcePlugin.class),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(ignored.config()),
                                    ApplicationServicesFixture.buildId(ignored.resources()),
                                    ApplicationServicesFixture.messages(ignored.dataSource())),
                            ApplicationServicesFixture.audit(ignored.dataSource()),
                            ApplicationServicesFixture.operations(ignored.dataSource()),
                            AccountServicesFixture.component(ignored.dataSource()).requestIdentity());
            setControllerFields(controller, request("/api/admin/static-site", Map.of()), response());

            AdminPageDataResponse<AdminStaticSiteSyncResponse> response = controller.index();

            assertTrue(response.getData().getSynced());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRenderAdminStaticSiteSyncSuccessWhenStaticSiteIsDisabled() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ResponseRecorder recorder = new ResponseRecorder();
            AdminStaticSiteController controller =
                    new AdminStaticSiteController(
                            ApplicationServicesFixture.cacheRefresh(null, ignored.config()),
                            ApplicationServicesFixture.publisher(null, ignored.config()),
                            () -> ignored.config().getPlugin(AdminStaticResourcePlugin.class),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(ignored.config()),
                                    ApplicationServicesFixture.buildId(ignored.resources()),
                                    ApplicationServicesFixture.messages(ignored.dataSource())),
                            ApplicationServicesFixture.audit(ignored.dataSource()),
                            ApplicationServicesFixture.operations(ignored.dataSource()),
                            AccountServicesFixture.component(ignored.dataSource()).requestIdentity());
            setControllerFields(controller, request("/api/admin/static-site/sync", Map.of()), recorder.response());

            controller.startSync();

            ApiStandardResponse<AdminStaticSiteSyncResponse> rendered =
                    (ApiStandardResponse<AdminStaticSiteSyncResponse>) recorder.rendered;
            assertEquals(0, rendered.getError());
            assertTrue(rendered.getData().getSynced());
        }
    }

    @Test
    public void shouldRejectAdminStaticSiteRequestsWhenEnabledPluginIsMissing() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.unitCacheService().getPublicWebSiteInfo().setGenerator_html_status(true);
            AdminStaticSiteController controller =
                    new AdminStaticSiteController(
                            ApplicationServicesFixture.cacheRefresh(null, db.config()),
                            ApplicationServicesFixture.publisher(null, db.config()),
                            () -> db.config().getPlugin(AdminStaticResourcePlugin.class),
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(db.config()),
                                    ApplicationServicesFixture.buildId(db.resources()),
                                    ApplicationServicesFixture.messages(db.dataSource())),
                            ApplicationServicesFixture.audit(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()),
                            AccountServicesFixture.component(db.dataSource()).requestIdentity());
            setControllerFields(controller, request("/api/admin/static-site", Map.of()), response());

            assertThrows(NotFindResourceException.class, controller::index);

            setControllerFields(controller, request("/api/admin/static-site/sync", Map.of()), response());
            assertThrows(NotFindResourceException.class, controller::startSync);
        }
    }

    private static class ResponseRecorder {
        private Object rendered;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminSystemAndStaticSiteControllerTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("renderJson".equals(method.getName())) {
                            rendered = args[0];
                        }
                        return null;
                    });
        }
    }
}
