package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminLoginInterceptorPasskeyTest {

    private final AdminLoginInterceptor interceptor = new AdminLoginInterceptor();

    @Test
    public void shouldAllowOnlyPasskeyAuthenticationEndpointsWithoutLogin() {
        assertTrue(interceptor.isHandleAble(request("/api/admin/passkey/authentication/options")));
        assertTrue(interceptor.isHandleAble(request("/api/admin/passkey/authentication/verify")));

        assertFalse(interceptor.isHandleAble(request("/api/admin/passkey/authentication/options/extra")));
        assertFalse(interceptor.isHandleAble(request("/api/admin/account-security/passkeys")));
        assertFalse(interceptor.isHandleAble(
                request("/api/admin/account-security/passkey/registration/options")));
        assertFalse(interceptor.isHandleAble(
                request("/api/admin/account-security/passkey/registration/verify")));
        assertFalse(interceptor.isHandleAble(request("/api/admin/account-security/passkey/remove")));
    }

    private static HttpRequest request(String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminLoginInterceptorPasskeyTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }
}
