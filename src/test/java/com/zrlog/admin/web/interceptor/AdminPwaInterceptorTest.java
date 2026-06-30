package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminManifestResponse;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.AdminController;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AdminPwaInterceptorTest {

    @Test
    public void shouldRenderServiceWorkerThroughConfiguredAdminResource() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ResponseRecorder response = new ResponseRecorder();

            new AdminPwaInterceptor().doInterceptor(
                    request(AdminConstants.ADMIN_SERVICE_WORKER_JS, new ServerConfig()), response.response());

            assertTrue(response.headers.get("Content-Type").contains("javascript"));
            assertNotNull(response.written);
        }
    }

    @Test
    public void shouldRenderPackagedPwaResourceWhenPresent() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new AdminPwaInterceptor().doInterceptor(
                request(AdminResource.ADMIN_ASSET_MANIFEST_JSON, new ServerConfig()), response.response());

        assertTrue(response.headers.get("Content-Type").contains("json"));
        assertTrue(response.written.length > 0);
    }

    @Test
    public void shouldRenderManifestJsonThroughRouterAndRealWebsiteConfig() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ResponseRecorder response = new ResponseRecorder();
            ServerConfig serverConfig = new ServerConfig();
            Router router = serverConfig.getRouter();
            router.addMapper(AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH, AdminController.class, "manifest");

            new AdminPwaInterceptor().doInterceptor(
                    request(AdminConstants.ADMIN_PWA_MANIFEST_JSON, serverConfig), response.response());

            AdminManifestResponse manifest = (AdminManifestResponse) response.renderedJson;
            assertNotNull(manifest);
            assertEquals("ZrLog Test", manifest.getShort_name());
            assertTrue(manifest.getIcons().get(0).getSrc().contains("/admin/pwa/icon/"));
        }
    }

    @Test
    public void shouldRender404WhenPwaResourceIsMissing() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new AdminPwaInterceptor().doInterceptor(
                request("/admin/pwa/missing.json", new ServerConfig()), response.response());

        assertEquals(Integer.valueOf(404), response.renderedCode);
    }

    private static HttpRequest request(String uri, ServerConfig serverConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminPwaInterceptorTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return uri;
                        case "getMethod":
                            return HttpMethod.GET;
                        case "getHeader":
                            return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap":
                        case "getParamMap":
                        case "decodeParamMap":
                            return Map.of();
                        case "getContextPath":
                            return "/blog";
                        case "getScheme":
                            return "http";
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

    private static class ResponseRecorder {
        private final Map<String, String> headers = new HashMap<>();
        private byte[] written;
        private Object renderedJson;
        private Integer renderedCode;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminPwaInterceptorTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addHeader":
                                headers.put(args[0].toString(), args[1].toString());
                                return null;
                            case "write":
                                if (args[0] instanceof InputStream) {
                                    written = ((InputStream) args[0]).readAllBytes();
                                }
                                return null;
                            case "renderJson":
                                renderedJson = args[0];
                                return null;
                            case "renderCode":
                                renderedCode = (Integer) args[0];
                                return null;
                            default:
                                return null;
                        }
                    });
        }
    }
}
