package com.zrlog.admin.web.controller.page;

import com.zrlog.admin.web.page.AdminPageRequestHandler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminPageService;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.WebSite;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

public class AdminPageControllerTest {

    private ZrLogConfig applicationConfig;

    @Test
    public void shouldRedirectAdminRootToIndexPage() throws Throwable {
        ResponseRecorder recorder = new ResponseRecorder();
        AdminPageController controller = controller(Constants.ADMIN_URI_BASE_PATH, Map.of(), recorder.response(), null);

        controller.index();

        assertEquals(Constants.ADMIN_URI_BASE_PATH + AdminConstants.INDEX_URI_PATH, recorder.redirect);
    }

    @Test
    public void shouldReturnServerSideDataForAnonymousPageRequest() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            AdminTokenThreadLocal.remove();
            AdminPageController controller = controller(Constants.ADMIN_URI_BASE_PATH + "/system",
                    Map.of("uri", "/system"), response(), ignored.dataSource());

            AdminPageDataResponse<ServerSideDataResponse<Object>> response = controller.ssJson();

            assertNotNull(response.getData());
            assertEquals(null, response.getData().getUser());
            assertNotNull(response.getData().getResourceInfo());
            assertEquals(null, response.getData().getKey());
        }
    }

    private AdminPageController controller(String uri, Map<String, String> params, HttpResponse response, DataSourceWrapper testDataSource)
            throws Exception {
        var site = ApplicationServicesFixture.site(applicationConfig);
        var pages =
                new AdminPageDataFactory(
                        site,
                        () -> "test",
                        ApplicationServicesFixture.messages(testDataSource));
        var resources =
                new AdminResourceService(
                        AdminResourceInfoResponse.Capabilities::new,
                        new FeatureLabService(
                                () -> true,
                                new WebhookService(
                                        ApplicationServicesFixture.messages(testDataSource),
                                        ApplicationServicesFixture.websiteCache(testDataSource)),
                                () -> new WebSite(testDataSource)),
                        site,
                        () -> "",
                        () -> new UserPasskey(testDataSource));
        AdminPageController controller =
                new AdminPageController(
                        new AdminPageRequestHandler(
                                AccountServicesFixture.component(testDataSource).requestIdentity(),
                                AccountServicesFixture.component(testDataSource).routeAuthorizer(),
                                new AdminPageService(
                                new InMemoryZrLogDatabase.TestCacheService(),
                                pages,
                                resources,
                                () -> "test",
                                AccountServicesFixture.component(testDataSource).identities(),
                                AccountServicesFixture.component(testDataSource).preferences(),
                                AccountServicesFixture.component(testDataSource).users()), pages),
                        applicationConfig == null ? null : applicationConfig.getTokenService());
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request(uri, params));
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response);
        return controller;
    }

    private static HttpRequest request(String uri, Map<String, String> params) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminPageControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return uri;
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getHeader":
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
                AdminPageControllerTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    private static class ResponseRecorder {
        private String redirect;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminPageControllerTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("redirect".equals(method.getName())) {
                            redirect = args[0].toString();
                        }
                        return null;
                    });
        }
    }
}
