package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PasskeyRequestContextTest {

    private final PasskeyRequestContext requestContext = new PasskeyRequestContext();

    @Test
    public void shouldAcceptSameOriginHttpsRequest() {
        PasskeyRequestContext.Context context = requestContext.resolve(
                request("https://admin.example.com:8443", "admin.example.com:8443", "https"));

        assertEquals("https://admin.example.com:8443", context.getOrigin());
        assertEquals("admin.example.com", context.getRpId());
        assertEquals("https", context.getProtocol());
    }

    @Test
    public void shouldAcceptHttpRequestForLocalhost() {
        PasskeyRequestContext.Context context = requestContext.resolve(
                request("http://localhost:18080", "localhost:18080", "http"));

        assertEquals("http://localhost:18080", context.getOrigin());
        assertEquals("localhost", context.getRpId());
        assertEquals("http", context.getProtocol());
    }

    @Test
    public void shouldAcceptConfiguredStaticAdminOriginWithoutUsingApiHostAsRpId() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "demo.zrlog.com");

        PasskeyRequestContext.Context context = configuredContext.resolve(
                request("https://demo.zrlog.com", "faas-demo.zrlog.com", "https"));

        assertEquals("https://demo.zrlog.com", context.getOrigin());
        assertEquals("demo.zrlog.com", context.getRpId());
        assertEquals("https", context.getProtocol());
    }

    @Test
    public void shouldAcceptConfiguredLocalhostAcrossPorts() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "localhost:18080");

        PasskeyRequestContext.Context context = configuredContext.resolve(
                request("http://localhost:18080", "localhost:28080", "http"));

        assertEquals("http://localhost:18080", context.getOrigin());
        assertEquals("localhost", context.getRpId());
    }

    @Test
    public void shouldResolvePageContextFromRequestWhenOriginHeaderIsAbsent() {
        PasskeyRequestContext.Context context = requestContext.resolvePage(
                request(null, "admin.example.com:8443", "https"));

        assertEquals("https://admin.example.com:8443", context.getOrigin());
        assertEquals("admin.example.com", context.getRpId());
        assertEquals("https", context.getProtocol());
    }

    @Test
    public void shouldNormalizeConfiguredHostForGeneratedPageContext() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "demo.zrlog.com/");

        PasskeyRequestContext.Context context = configuredContext.resolvePage(
                request(null, "demo.zrlog.com/", "https"));

        assertEquals("https://demo.zrlog.com", context.getOrigin());
        assertEquals("demo.zrlog.com", context.getRpId());
    }

    @Test
    public void shouldApplyTrustedOriginValidationWhenResolvingPageContext() {
        PasskeyRequestContext.Context context = requestContext.resolvePage(
                request("https://admin.example.com", "admin.example.com", "https"));

        assertEquals("https://admin.example.com", context.getOrigin());
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolvePage(
                request("https://untrusted.example.net", "admin.example.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolvePage(
                request("", "admin.example.com", "https")));
    }

    @Test
    public void shouldRejectCrossOriginRequest() {
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://admin.example.com", "other.example.com", "https")));
    }

    @Test
    public void shouldRejectOriginsOutsideExactConfiguredStaticAdminOrigin() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "demo.zrlog.com");

        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://preview.demo.zrlog.com", "faas-demo.zrlog.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://demo.zrlog.com:8443", "faas-demo.zrlog.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://evil.example.com", "faas-demo.zrlog.com", "https")));
    }

    @Test
    public void shouldRejectInsecureCrossOriginApiAndInvalidConfiguredHosts() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "demo.zrlog.com");

        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://demo.zrlog.com", "faas-demo.zrlog.com", "http")));
        assertThrows(PasskeyVerificationException.class, () -> new PasskeyRequestContext(
                () -> "https://demo.zrlog.com/admin").resolve(
                request("https://demo.zrlog.com", "faas-demo.zrlog.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> new PasskeyRequestContext(
                () -> "https://user@demo.zrlog.com").resolve(
                request("https://demo.zrlog.com", "faas-demo.zrlog.com", "https")));
    }

    @Test
    public void shouldRejectMalformedApiAuthorities() {
        PasskeyRequestContext configuredContext = new PasskeyRequestContext(() -> "demo.zrlog.com");

        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://demo.zrlog.com", "user@faas-demo.zrlog.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> configuredContext.resolve(
                request("https://demo.zrlog.com", "faas-demo.zrlog.com:70000", "https")));
    }

    @Test
    public void shouldRejectInsecureRequestForNonLocalHost() {
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("http://admin.example.com", "admin.example.com", "http")));
    }

    @Test
    public void shouldRejectIpLiteralRpIds() {
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("http://127.0.0.1:18080", "127.0.0.1:18080", "http")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://127.0.0.1:18443", "127.0.0.1:18443", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("http://[::1]:18080", "[::1]:18080", "http")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://2130706433", "2130706433", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://0x7f000001", "0x7f000001", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://0177.0.0.1", "0177.0.0.1", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://demo.zrlog.com.", "demo.zrlog.com.", "https")));
    }

    @Test
    public void shouldRejectRequestWithoutRequiredOriginOrHostHeader() {
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request(null, "admin.example.com", "https")));
        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://admin.example.com", null, "https")));
    }

    @Test
    public void shouldRejectOriginThatCannotFitPersistedPasskeyContext() {
        String oversizedHost = String.join(".",
                "a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(63), "example");

        assertThrows(PasskeyVerificationException.class, () -> requestContext.resolve(
                request("https://" + oversizedHost, oversizedHost, "https")));
    }

    private static HttpRequest request(String origin, String host, String scheme) {
        return (HttpRequest) Proxy.newProxyInstance(
                PasskeyRequestContextTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader":
                            if ("Origin".equals(args[0])) {
                                return origin;
                            }
                            if ("Host".equals(args[0])) {
                                return host;
                            }
                            return null;
                        case "getScheme":
                            return scheme;
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
}
