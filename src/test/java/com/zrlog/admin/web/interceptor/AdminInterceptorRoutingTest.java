package com.zrlog.admin.web.interceptor;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdminInterceptorRoutingTest {

    @Test
    public void shouldMatchAdminStaticResourceRoutes() {
        AdminStaticResourceInterceptor interceptor = new AdminStaticResourceInterceptor();

        assertTrue(interceptor.isHandleAble(request(AdminConstants.ADMIN_SERVICE_WORKER_JS)));
        assertTrue(interceptor.isHandleAble(request("/admin/static/js/main.123.js")));
        assertTrue(interceptor.isHandleAble(request("/admin/pwa/icon/favicon-192.png")));
        assertFalse(interceptor.isHandleAble(request("/admin/index")));
        assertFalse(interceptor.isHandleAble(request("/admin/static/js/main.123.js",
                BaseStaticSitePlugin.STATIC_USER_AGENT)));
    }

    @Test
    public void shouldMatchAdminPluginRoutes() {
        AdminPluginInterceptor interceptor = new AdminPluginInterceptor();

        assertTrue(interceptor.isHandleAble(request("/admin/plugins")));
        assertTrue(interceptor.isHandleAble(request("/admin/plugins/reminder")));
        assertFalse(interceptor.isHandleAble(request("/admin/plugin/reminder")));
    }

    @Test
    public void shouldMatchCoreAdminRoutes() {
        AdminInterceptor interceptor = new AdminInterceptor();

        assertTrue(interceptor.isHandleAble(request("/admin")));
        assertTrue(interceptor.isHandleAble(request("/api/admin")));
        assertTrue(interceptor.isHandleAble(request("/admin/index")));
        assertTrue(interceptor.isHandleAble(request("/api/admin/article")));
        assertFalse(interceptor.isHandleAble(request("/api/public/version")));
    }

    @Test
    public void shouldMatchLoginAndRefreshRoutes() {
        AdminLoginInterceptor loginInterceptor = new AdminLoginInterceptor();
        AdminRefreshCacheInterceptor refreshCacheInterceptor = new AdminRefreshCacheInterceptor();

        assertTrue(loginInterceptor.isHandleAble(request(AdminConstants.ADMIN_LOGIN_URI_PATH)));
        assertTrue(loginInterceptor.isHandleAble(request("/admin/logout")));
        assertTrue(loginInterceptor.isHandleAble(request("/api" + AdminConstants.ADMIN_LOGIN_URI_PATH)));
        assertFalse(loginInterceptor.isHandleAble(request("/admin/index")));
        assertTrue(refreshCacheInterceptor.isHandleAble(request(AdminConstants.ADMIN_REFRESH_CACHE_API_URI_PATH)));
        assertFalse(refreshCacheInterceptor.isHandleAble(request("/api/admin/cache")));
    }

    @Test
    public void shouldMatchTemporaryDevAndPwaRoutes() {
        assertTrue(new AdminTemporaryResourceInterceptor().isHandleAble(
                request(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/image.png")));
        assertFalse(new AdminTemporaryResourceInterceptor().isHandleAble(request("/attached/tmp/image.png")));
        assertTrue(new AdminDevFileInterceptor().isHandleAble(request("/admin/dev/file/tmp/a.txt")));
        assertFalse(new AdminDevFileInterceptor().isHandleAble(request("/admin/dev")));

        PwaInterceptor pwaInterceptor = new PwaInterceptor();
        assertTrue(pwaInterceptor.isHandleAble(request(AdminConstants.FAVICON_ICO_URI_PATH)));
        assertTrue(pwaInterceptor.isHandleAble(request(AdminConstants.FAVICON_PNG_PWA_192_URI_PATH)));
        assertTrue(pwaInterceptor.isHandleAble(request(AdminConstants.FAVICON_PNG_PWA_512_URI_PATH)));
        assertFalse(pwaInterceptor.isHandleAble(request("/admin/pwa/icon/other.png")));

        AdminPwaInterceptor adminPwaInterceptor = new AdminPwaInterceptor();
        assertTrue(adminPwaInterceptor.isHandleAble(request(AdminConstants.ADMIN_PWA_MANIFEST_JSON)));
        assertTrue(adminPwaInterceptor.isHandleAble(request(AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH)));
        assertTrue(adminPwaInterceptor.isHandleAble(request(AdminConstants.ADMIN_SERVICE_WORKER_JS)));
        assertFalse(adminPwaInterceptor.isHandleAble(request("/admin/pwa/missing.json")));
    }

    @Test
    public void shouldRedirectMappedAdminRouteWhenAdminTokenIsMissing() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(null)), () ->
                new AdminInterceptor().doInterceptor(
                        adminRequest(HttpMethod.GET, "/admin/interceptor/plain"), response.response()));

        assertTrue(response.redirected.contains("/admin/login"));
        assertTrue(response.redirected.contains("redirectFrom="));
        assertEquals("0", response.contentLengthAtRedirect);
    }

    @Test
    public void shouldInvokeMappedAdminRouteWithTokenAndClearThreadLocal() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(token())), () ->
                new AdminInterceptor().doInterceptor(
                        adminRequest(HttpMethod.GET, "/admin/interceptor/plain"), response.response()));

        assertEquals("plain", ((ApiStandardResponse<?>) response.rendered).getData());
        assertNull(AdminTokenThreadLocal.getUser());
    }

    @Test
    public void shouldInvokeLockedAdminRouteThroughRefreshAwareInterceptor() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ResponseRecorder response = new ResponseRecorder();

            withConfig(new TestZrLogConfig(tokenService(token())), () ->
                    new AdminInterceptor().doInterceptor(
                            adminRequest(HttpMethod.POST, "/admin/interceptor/locked"), response.response()));

            assertEquals("locked", ((ApiStandardResponse<?>) response.rendered).getData());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void shouldSkipPostOnlyRequestLockForGetRequest() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(token())), () ->
                new AdminInterceptor().doInterceptor(
                        adminRequest(HttpMethod.GET, "/admin/interceptor/postOnlyLock"), response.response()));

        assertEquals("postOnly", ((ApiStandardResponse<?>) response.rendered).getData());
        assertNull(AdminTokenThreadLocal.getUser());
    }

    private static HttpRequest request(String uri) {
        return request(uri, null);
    }

    private static HttpRequest request(String uri, String userAgent) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminInterceptorRoutingTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getHeader".equals(method.getName()) && "User-Agent".equals(args[0])) {
                        return userAgent;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpRequest adminRequest(HttpMethod method, String uri) {
        ServerConfig serverConfig = new ServerConfig();
        Router router = serverConfig.getRouter();
        router.addMapper("/admin/interceptor", InterceptorController.class);
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(router);
        return (HttpRequest) Proxy.newProxyInstance(
                AdminInterceptorRoutingTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, calledMethod, args) -> {
                    switch (calledMethod.getName()) {
                        case "getUri":
                            return uri;
                        case "getMethod":
                            return method;
                        case "getContextPath":
                            return "";
                        case "getHeader":
                            return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap":
                        case "getParamMap":
                        case "decodeParamMap":
                            return Map.of();
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getServerConfig":
                            return serverConfig;
                        case "getRequestConfig":
                            return requestConfig;
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (calledMethod.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static void withConfig(ZrLogConfig config, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = config;
            runnable.run();
        } finally {
            Constants.zrLogConfig = previous;
            AdminTokenThreadLocal.remove();
        }
    }

    private static AdminFullTokenVO token() {
        AdminFullTokenVO token = new AdminFullTokenVO();
        token.setUserId(1);
        token.setSessionId("session-1");
        token.setProtocol("http");
        token.setSecretKey("secret");
        return token;
    }

    private static TokenService tokenService(AdminFullTokenVO token) {
        return new TokenService() {
            @Override
            public void updateSessionTimeout(long sessionTimeoutInMinutes) {
            }

            @Override
            public AdminFullTokenVO getAdminTokenVO(HttpRequest request) {
                return token;
            }

            @Override
            public void removeAdminToken(HttpRequest request, HttpResponse response) {
            }

            @Override
            public void setAdminToken(Integer userId, String secretKey, String sessionId, String protocol,
                                      HttpRequest request, HttpResponse response) {
                try {
                    AdminFullTokenVO bound = new AdminFullTokenVO();
                    bound.setUserId(userId);
                    bound.setSecretKey(secretKey);
                    bound.setSessionId(sessionId);
                    bound.setProtocol(protocol);
                    Method method = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken",
                            com.zrlog.common.vo.AdminTokenVO.class);
                    method.setAccessible(true);
                    method.invoke(null, bound);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static class InterceptorController extends Controller {

        @ResponseBody
        public ApiStandardResponse<String> plain() {
            return new ApiStandardResponse<>("plain");
        }

        @ResponseBody
        @RequestLock
        public ApiStandardResponse<String> locked() {
            return new ApiStandardResponse<>("locked");
        }

        @ResponseBody
        @RequestLock(onlyOnPostMethod = true)
        public ApiStandardResponse<String> postOnlyLock() {
            return new ApiStandardResponse<>("postOnly");
        }
    }

    private static class ResponseRecorder {
        private String contentLength;
        private String contentLengthAtRedirect;
        private Object rendered;
        private String redirected;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminInterceptorRoutingTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addHeader":
                                if ("Content-Length".equals(args[0])) {
                                    contentLength = args[1].toString();
                                }
                                return null;
                            case "renderJson":
                                rendered = args[0];
                                return null;
                            case "redirect":
                                contentLengthAtRedirect = contentLength;
                                redirected = args[0].toString();
                                return null;
                            default:
                                return null;
                        }
                    });
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final TokenService tokenService;
        private boolean installed;

        TestZrLogConfig(TokenService tokenService) {
            super(18080, null, "");
            this.tokenService = tokenService;
            this.installed = true;
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return tokenService;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }
}
