package com.zrlog.admin.web.interceptor;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdminPluginInterceptorTest {

    @Test
    public void shouldTreatOnlyPluginRootPwaResourcesAsPublic() {
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.webmanifest"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.json"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-icon"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-sw.js"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.webmanifest?v=1"));

        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/static/app.js"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/api/status"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/nested/manifest.json"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-icon/"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugin/reminder/manifest.json"));
    }

    @Test
    public void shouldConvertAdminPluginPathToPluginCorePath() {
        assertEquals("/reminder/manifest.webmanifest",
                AdminPluginInterceptor.pluginCoreUri("/admin/plugins/reminder/manifest.webmanifest"));
    }

    @Test
    public void shouldRedirectPluginRootToSlashPath() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new AdminPluginInterceptor().doInterceptor(
                request(HttpMethod.GET, "/admin/plugins"), response.response());

        assertEquals("/admin/plugins/", response.redirected);
    }

    @Test
    public void shouldProxyPublicPluginPwaResourceWithoutAdminToken() throws Exception {
        FakePluginCorePlugin plugin = new FakePluginCorePlugin(true);

        withConfig(new TestZrLogConfig(tokenService(null), plugin), () ->
                new AdminPluginInterceptor().doInterceptor(
                        request(HttpMethod.GET, "/admin/plugins/reminder/manifest.json"), new ResponseRecorder().response()));

        assertEquals("/reminder/manifest.json", plugin.lastUri);
        assertNull(plugin.lastToken);
    }

    @Test
    public void shouldRender404WhenPublicPluginPwaResourceIsMissing() throws Exception {
        FakePluginCorePlugin plugin = new FakePluginCorePlugin(false);
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(null), plugin), () ->
                new AdminPluginInterceptor().doInterceptor(
                        request(HttpMethod.GET, "/admin/plugins/reminder/manifest.json"), response.response()));

        assertEquals(Integer.valueOf(404), response.renderedCode);
    }

    @Test
    public void shouldRedirectUnauthorizedPluginSurfaceRequest() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(null), new FakePluginCorePlugin(true)), () ->
                new AdminPluginInterceptor().doInterceptor(
                        request(HttpMethod.GET, "/admin/plugins/reminder/index"), response.response()));

        assertTrue(response.redirected.contains("/admin/login"));
        assertTrue(response.redirected.contains("redirectFrom="));
    }

    @Test
    public void shouldRender404WhenAuthorizedPluginAccessFails() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        withConfig(new TestZrLogConfig(tokenService(token()), new FakePluginCorePlugin(false)), () ->
                new AdminPluginInterceptor().doInterceptor(
                        request(HttpMethod.GET, "/admin/plugins/reminder/index"), response.response()));

        assertEquals(Integer.valueOf(404), response.renderedCode);
    }

    @Test
    public void shouldRecordPluginSurfaceActionThroughRealAuditStore() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FakePluginCorePlugin plugin = new FakePluginCorePlugin(true);

            withConfig(new TestZrLogConfig(tokenService(token()), plugin), () ->
                    new AdminPluginInterceptor().doInterceptor(
                            request(HttpMethod.POST, "/admin/plugins/reminder/surfaceAction"), new ResponseRecorder().response()));

            assertEquals("/reminder/surfaceAction", plugin.lastUri);
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("PLUGIN_SURFACE_ACTION"));
        }
    }

    private static void withConfig(ZrLogConfig config, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = config;
            runnable.run();
        } finally {
            Constants.zrLogConfig = previous;
        }
    }

    private static HttpRequest request(HttpMethod method, String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminPluginInterceptorTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, calledMethod, args) -> {
                    switch (calledMethod.getName()) {
                        case "getMethod":
                            return method;
                        case "getUri":
                            return uri;
                        case "getContextPath":
                            return "/blog";
                        case "getHeader":
                            return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
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
            }
        };
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class ResponseRecorder {
        private String redirected;
        private Integer renderedCode;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminPluginInterceptorTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "redirect":
                                redirected = args[0].toString();
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

    private static class TestZrLogConfig extends ZrLogConfig {

        private final TokenService tokenService;

        TestZrLogConfig(TokenService tokenService, PluginCorePlugin plugin) {
            super(18080, null, "");
            this.tokenService = tokenService;
            getAllPlugins().add(plugin);
        }

        @Override
        public boolean isInstalled() {
            return true;
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

    private static class FakePluginCorePlugin implements PluginCorePlugin {

        private final boolean accessResult;
        private String lastUri;
        private AdminTokenVO lastToken;

        FakePluginCorePlugin(boolean accessResult) {
            this.accessResult = accessResult;
        }

        @Override
        public boolean refreshCache(String cacheVersion, HttpRequest request) {
            return false;
        }

        @Override
        public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request,
                                              AdminTokenVO adminTokenVO) {
            return null;
        }

        @Override
        public <T> T requestService(HttpRequest inputRequest, Map<String, String[]> params, AdminTokenVO adminTokenVO,
                                    Class<T> clazz) {
            return null;
        }

        @Override
        public boolean accessPlugin(String uri, HttpRequest request, HttpResponse response, AdminTokenVO adminTokenVO)
                throws IOException, URISyntaxException, InterruptedException {
            this.lastUri = uri;
            this.lastToken = adminTokenVO;
            return accessResult;
        }

        @Override
        public String getToken() {
            return "token";
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }
}
