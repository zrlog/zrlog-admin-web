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
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AdminStaticResourceInterceptorTest {

    @Test
    public void shouldRenderAdminMainJsWithJavascriptContentType() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        new AdminStaticResourceInterceptor().doInterceptor(
                request("/admin/static/js/main.missing.js", new ServerConfig(), requestConfig(new Router())),
                response.response());

        assertEquals("application/javascript;charset=UTF-8", response.headers.get("Content-Type"));
        assertArrayEquals(new byte[0], response.written);
        assertEquals(Integer.valueOf(200), response.status);
    }

    @Test
    public void shouldFallbackToMethodInterceptorWhenNoCachedStaticFileExists() throws Exception {
        ResponseRecorder response = new ResponseRecorder();
        ServerConfig serverConfig = new ServerConfig();
        Router router = serverConfig.getRouter();
        router.addMapper("/admin/static/fallback", StaticFallbackController.class, "index");

        withConfig(new TestZrLogConfig(), () ->
                new AdminStaticResourceInterceptor().doInterceptor(
                        request("/admin/static/fallback", serverConfig, requestConfig(router)), response.response()));

        assertEquals("fallback", ((ApiStandardResponse<?>) response.rendered).getData());
    }

    private static RequestConfig requestConfig(Router router) {
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(router);
        return requestConfig;
    }

    private static HttpRequest request(String uri, ServerConfig serverConfig, RequestConfig requestConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminStaticResourceInterceptorTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri":
                            return uri;
                        case "getMethod":
                            return HttpMethod.GET;
                        case "getContextPath":
                            return "";
                        case "getRequestConfig":
                            return requestConfig;
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

    private static void withConfig(ZrLogConfig config, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = config;
            runnable.run();
        } finally {
            Constants.zrLogConfig = previous;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static class StaticFallbackController extends Controller {

        @ResponseBody
        public ApiStandardResponse<String> index() {
            return new ApiStandardResponse<>("fallback");
        }
    }

    private static class ResponseRecorder {
        private final Map<String, String> headers = new HashMap<>();
        private byte[] written;
        private Integer status;
        private Object rendered;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminStaticResourceInterceptorTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getHeader":
                                return headers;
                            case "addHeader":
                                headers.put(args[0].toString(), args[1].toString());
                                return null;
                            case "write":
                                if (args[0] instanceof InputStream) {
                                    written = ((InputStream) args[0]).readAllBytes();
                                }
                                if (args.length > 1) {
                                    status = (Integer) args[1];
                                }
                                return null;
                            case "writeFile":
                                written = java.nio.file.Files.readAllBytes(((File) args[0]).toPath());
                                return null;
                            case "renderJson":
                                rendered = args[0];
                                return null;
                            default:
                                return null;
                        }
                    });
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        TestZrLogConfig() {
            super(18080, null, "");
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }
}
