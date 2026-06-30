package com.zrlog.admin.web.interceptor;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class AdminInterceptorSupportTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldReturnNullAdminTokenWhenBlogIsNotInstalled() throws Exception {
        withConfig(new TestZrLogConfig(false, tokenService(new AdminFullTokenVO())), () ->
                assertNull(AdminInterceptorSupport.getAdminToken(request())));
    }

    @Test
    public void shouldReturnNullAdminTokenWhenTokenServiceIsMissing() throws Exception {
        withConfig(new TestZrLogConfig(true, null), () ->
                assertNull(AdminInterceptorSupport.getAdminToken(request())));
    }

    @Test
    public void shouldReturnAdminTokenFromConfiguredTokenService() throws Exception {
        AdminFullTokenVO token = new AdminFullTokenVO();

        withConfig(new TestZrLogConfig(true, tokenService(token)), () ->
                assertSame(token, AdminInterceptorSupport.getAdminToken(request())));
    }

    @Test
    public void shouldRunNormalRefreshAwareInterceptorForPlainControllerMethod() throws Exception {
        ResponseRecorder response = new ResponseRecorder();
        RequestConfig requestConfig = requestConfig();
        Method method = RefreshController.class.getDeclaredMethod("plain");

        AdminInterceptorSupport.doRefreshAwareMethodInterceptor(
                request("/plain", HttpMethod.GET, Map.of(), requestConfig), response.response(), method);

        assertEquals("plain", ((ApiStandardResponse<?>) response.rendered).getData());
    }

    @Test
    public void shouldSkipPostOnlyRefreshCacheForGetRequest() throws Exception {
        ResponseRecorder response = new ResponseRecorder();
        RequestConfig requestConfig = requestConfig();
        Method method = RefreshController.class.getDeclaredMethod("refreshPostOnly");

        AdminInterceptorSupport.doRefreshAwareMethodInterceptor(
                request("/refreshPostOnly", HttpMethod.GET, Map.of(), requestConfig), response.response(), method);

        assertEquals("refresh", ((ApiStandardResponse<?>) response.rendered).getData());
    }

    @Test
    public void shouldFallbackToMethodInterceptorWhenSseRefreshIsGetButOnlyPostIsAllowed() throws Exception {
        ResponseRecorder response = new ResponseRecorder();
        RequestConfig requestConfig = requestConfig();
        Method method = RefreshController.class.getDeclaredMethod("refreshPostOnly");

        AdminInterceptorSupport.doRefreshAwareMethodInterceptor(
                request("/refreshPostOnly", HttpMethod.GET, Map.of("Accept", "text/event-stream"), requestConfig),
                response.response(), method);

        assertEquals("refresh", ((ApiStandardResponse<?>) response.rendered).getData());
    }

    private void withConfig(ZrLogConfig config, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-admin-interceptor").getAbsolutePath());
            Constants.zrLogConfig = config;
            runnable.run();
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
        }
    }

    private static HttpRequest request() {
        return request("/", HttpMethod.GET, Map.of(), null);
    }

    private static HttpRequest request(String uri, HttpMethod httpMethod, Map<String, String> headers,
                                       RequestConfig requestConfig) {
        ServerConfig serverConfig = new ServerConfig();
        return (HttpRequest) Proxy.newProxyInstance(
                AdminInterceptorSupportTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return uri;
                        case "getMethod":
                            return httpMethod;
                        case "getHeader":
                            return headers.get(args[0].toString());
                        case "getHeaderMap":
                        case "getParamMap":
                        case "decodeParamMap":
                            return Map.of();
                        case "getContextPath":
                            return "";
                        case "getServerConfig":
                            return serverConfig;
                        case "getRequestConfig":
                            return requestConfig;
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

    private static RequestConfig requestConfig() {
        Router router = new Router();
        router.addMapper("", RefreshController.class);
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(router);
        return requestConfig;
    }

    public static class RefreshController extends Controller {

        @ResponseBody
        public ApiStandardResponse<String> plain() {
            return new ApiStandardResponse<>("plain");
        }

        @ResponseBody
        @RefreshCache(updateStaticSites = StaticSiteType.BLOG, onlyOnPostMethod = true)
        public ApiStandardResponse<String> refreshPostOnly() {
            return new ApiStandardResponse<>("refresh");
        }
    }

    private static class ResponseRecorder {
        private Object rendered;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminInterceptorSupportTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("renderJson".equals(method.getName())) {
                            rendered = args[0];
                        }
                        return null;
                    });
        }
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

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final boolean installed;
        private final TokenService tokenService;

        TestZrLogConfig(boolean installed, TokenService tokenService) {
            super(18080, null, "");
            this.installed = installed;
            this.tokenService = tokenService;
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
