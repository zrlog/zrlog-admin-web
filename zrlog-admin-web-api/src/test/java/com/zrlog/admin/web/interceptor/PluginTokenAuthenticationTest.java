package com.zrlog.admin.web.interceptor;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.plugin.IPlugin;
import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class PluginTokenAuthenticationTest {
    @Test public void bothEndpointsRejectMissingAndWrongTokensAndAcceptHostTokenWithoutLogin() throws Exception {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new Config("host-token");
            AtomicInteger invoked = new AtomicInteger();
            AdminInterceptor refresh = new AdminRefreshCacheInterceptor() {
                @Override protected Method getMethod(HttpRequest request) { return null; }
                @Override protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) { invoked.incrementAndGet(); }
            };
            AdminInterceptor ai = new AdminInternalAiInterceptor() {
                @Override protected Method getMethod(HttpRequest request) { return null; }
                @Override protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) { invoked.incrementAndGet(); }
            };
            for (AdminInterceptor interceptor : List.of(refresh, ai)) {
                int before = invoked.get();
                assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of()), null));
                assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of("X-Plugin-Token", "wrong")), null));
                assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of("Cookie", "admin-token=some-cookie")), null));
                assertEquals(before, invoked.get());
                assertFalse(interceptor.doInterceptor(request(Map.of("X-Plugin-Token", "host-token")), null));
                assertEquals(before + 1, invoked.get());
            }
        } finally { Constants.zrLogConfig = previous; }
    }

    @Test public void refusesTokenWhenPluginConnectionIsUnavailable() {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new Config(null);
            assertThrows(ArgsException.class, () -> PluginTokenValidator.validate(request(Map.of("X-Plugin-Token", "host-token"))));
        } finally { Constants.zrLogConfig = previous; }
    }

    @Test public void internalInterceptorDoesNotBypassOtherAdminRoutes() {
        AdminInternalAiInterceptor interceptor = new AdminInternalAiInterceptor();
        assertTrue(interceptor.isHandleAble(request(Map.of("uri", AdminInternalAiInterceptor.COMMENT_ANALYZE_PATH))));
        assertFalse(interceptor.isHandleAble(request(Map.of("uri", "/api/admin/website/ai"))));
        assertFalse(interceptor.isHandleAble(request(Map.of("uri", "/api/admin/internal/ai/comment/analyze/other"))));
    }

    private HttpRequest request(Map<String, String> headers) {
        return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class}, (proxy, method, args) -> {
            if (method.getName().equals("getHeader")) return headers.get(args[0]);
            if (method.getName().equals("getUri")) return headers.getOrDefault("uri", AdminInternalAiInterceptor.COMMENT_ANALYZE_PATH);
            return null;
        });
    }

    private static class Config extends ZrLogConfig {
        private final String pluginToken;
        Config(String token) { super(18080, null, ""); this.pluginToken = token; }
        @Override public boolean isInstalled() { return true; }
        @Override public DataSourceWrapper configDatabase() { return null; }
        @Override protected TokenService initTokenService() { return null; }
        @Override public List<IPlugin> getBasePluginList() { return Collections.emptyList(); }
        @Override public <T extends IPlugin> T getPlugin(Class<T> type) {
            if (type != PluginCorePlugin.class || pluginToken == null) return null;
            return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginCorePlugin.class},
                    (proxy, method, args) -> method.getName().equals("getToken") ? pluginToken : null));
        }
    }
}
