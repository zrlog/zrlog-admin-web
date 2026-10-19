package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PluginTokenAuthenticationTest {

    @Test public void bothEndpointsRejectMissingAndWrongTokensAndAcceptHostTokenWithoutLogin() throws Exception {
        PluginTokenValidator tokens = new PluginTokenValidator(() -> plugin("host-token"));
        AtomicInteger invoked = new AtomicInteger();
        AdminRefreshCacheInterceptor refresh = new AdminRefreshCacheInterceptor(() -> null, tokens, () -> null) {
            @Override protected Method getMethod(HttpRequest request) { return null; }
            @Override protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) { invoked.incrementAndGet(); }
        };
        AdminMethodInterceptor ai = new AdminInternalAiInterceptor(tokens, () -> null) {
            @Override protected Method getMethod(HttpRequest request) { return null; }
            @Override protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) { invoked.incrementAndGet(); }
        };
        for (AdminMethodInterceptor interceptor : List.of(refresh, ai)) {
            int before = invoked.get();
            assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of()), null));
            assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of("X-Plugin-Token", "wrong")), null));
            assertThrows(ArgsException.class, () -> interceptor.doInterceptor(request(Map.of("Cookie", "admin-token=some-cookie")), null));
            assertEquals(before, invoked.get());
            assertFalse(interceptor.doInterceptor(request(Map.of("X-Plugin-Token", "host-token")), null));
            assertEquals(before + 1, invoked.get());
        }
    }

    @Test public void followsPluginCreationReplacementAndRemovalWithoutRecreatingValidator() {
        AtomicReference<PluginCorePlugin> current = new AtomicReference<>();
        PluginTokenValidator tokens = new PluginTokenValidator(current::get);
        HttpRequest firstToken = request(Map.of("X-Plugin-Token", "first-token"));
        HttpRequest secondToken = request(Map.of("X-Plugin-Token", "second-token"));
        assertThrows(ArgsException.class, () -> tokens.validate(firstToken));
        current.set(plugin("first-token"));
        tokens.validate(firstToken);
        assertThrows(ArgsException.class, () -> tokens.validate(secondToken));
        current.set(plugin("second-token"));
        assertThrows(ArgsException.class, () -> tokens.validate(firstToken));
        tokens.validate(secondToken);
        current.set(null);
        assertThrows(ArgsException.class, () -> tokens.validate(secondToken));
    }

    @Test public void refusesMissingHostTokensAndDoesNotAcceptAnotherApplicationsToken() {
        HttpRequest request = request(Map.of("X-Plugin-Token", "host-token"));
        for (PluginCorePlugin plugin : List.of(plugin(null), plugin(""), plugin("other-host-token"))) {
            assertThrows(ArgsException.class, () -> new PluginTokenValidator(() -> plugin).validate(request));
        }
    }

    @Test public void internalInterceptorDoesNotBypassOtherAdminRoutes() {
        AdminInternalAiInterceptor interceptor = new AdminInternalAiInterceptor(new PluginTokenValidator(() -> null), () -> null);
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

    private static PluginCorePlugin plugin(String token) {
        return (PluginCorePlugin) Proxy.newProxyInstance(PluginTokenAuthenticationTest.class.getClassLoader(),
                new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> method.getName().equals("getToken") ? token : null);
    }
}
