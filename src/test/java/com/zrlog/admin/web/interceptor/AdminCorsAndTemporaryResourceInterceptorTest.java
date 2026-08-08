package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.UploadFallbackZrLogConfig;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.common.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminCorsAndTemporaryResourceInterceptorTest {

    private com.zrlog.common.ZrLogConfig previousConfig;

    @Before
    public void setUp() {
        previousConfig = Constants.zrLogConfig;
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
    }

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
    }

    @Test
    public void shouldSkipCorsHeadersWhenOriginIsMissing() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.GET, "/api/admin", Map.of()), response.response());

        assertTrue(proceed);
        assertTrue(response.headers.isEmpty());
    }

    @Test
    public void shouldApplyCorsHeadersForOriginRequest() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.GET, "/api/admin", Map.of("Origin", "https://admin.example.com")),
                response.response());

        assertTrue(proceed);
        assertEquals("https://admin.example.com", response.headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", response.headers.get("Access-Control-Allow-Credentials"));
        assertTrue(response.headers.get("Access-Control-Allow-Headers")
                .contains(AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER));
    }

    @Test
    public void shouldVaryPublicAdminResourceByOrigin() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.GET, Constants.API_PUBLIC_ADMIN_RESOURCE, Map.of()), response.response());

        assertTrue(proceed);
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldCompleteCorsOptionsRequestWithoutContinuingChain() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.OPTIONS, "/api/admin", Map.of("Origin", "https://admin.example.com")),
                response.response());

        assertFalse(proceed);
        assertEquals(Integer.valueOf(200), response.writeStatus);
        assertEquals("POST, GET, OPTIONS, DELETE, PUT", response.headers.get("Access-Control-Allow-Methods"));
    }

    @Test
    public void shouldApplyCorsHeadersForTrustedPasskeyOrigin() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.POST, "/api/admin/passkey/authentication/options",
                        Map.of("Origin", "https://admin.example.com", "Host", "admin.example.com"), "https"),
                response.response());

        assertTrue(proceed);
        assertEquals("https://admin.example.com", response.headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", response.headers.get("Access-Control-Allow-Credentials"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldApplyCorsHeadersForConfiguredStaticAdminOrigin() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.cacheService().getPublicWebSiteInfo().setHost("demo.zrlog.com");
            ResponseRecorder response = new ResponseRecorder();

            boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                    request(HttpMethod.POST, "/api/admin/passkey/authentication/options",
                            Map.of("Origin", "https://demo.zrlog.com", "Host", "faas-demo.zrlog.com"), "https"),
                    response.response());

            assertTrue(proceed);
            assertEquals("https://demo.zrlog.com", response.headers.get("Access-Control-Allow-Origin"));
            assertEquals("true", response.headers.get("Access-Control-Allow-Credentials"));
            assertEquals("Origin", response.headers.get("Vary"));
        }
    }

    @Test
    public void shouldApplyCorsHeadersForSameOriginBrowserBehindReverseProxy() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.POST, "/api/admin/account-security/passkey/registration/options",
                        Map.of("Origin", "https://blog-admin.zrlog.com", "Host", "localhost:18080",
                                "Referer", "https://blog-admin.zrlog.com/admin/account-security",
                                "Sec-Fetch-Site", "same-origin"), "http"),
                response.response());

        assertTrue(proceed);
        assertEquals("https://blog-admin.zrlog.com", response.headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", response.headers.get("Access-Control-Allow-Credentials"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldNotApplyCorsHeadersForUntrustedPasskeyOrigin() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.POST, "/api/admin/passkey/authentication/options",
                        Map.of("Origin", "https://static.example.com", "Host", "admin.example.com"), "https"),
                response.response());

        assertTrue(proceed);
        assertFalse(response.headers.containsKey("Access-Control-Allow-Origin"));
        assertFalse(response.headers.containsKey("Access-Control-Allow-Credentials"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldRejectCorsOptionsForUntrustedPasskeyOrigin() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.OPTIONS, "/api/admin/account-security/passkey/registration/options",
                        Map.of("Origin", "https://static.example.com", "Host", "admin.example.com"), "https"),
                response.response());

        assertFalse(proceed);
        assertEquals(Integer.valueOf(403), response.renderedCode);
        assertFalse(response.headers.containsKey("Access-Control-Allow-Origin"));
        assertFalse(response.headers.containsKey("Access-Control-Allow-Methods"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldProtectPasskeyRoutesResolvedFromHyphenatedAliases() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.OPTIONS, "/api/admin/passkey/authentication/options-alias",
                        Map.of("Origin", "https://static.example.com", "Host", "admin.example.com"), "https"),
                response.response());

        assertFalse(proceed);
        assertEquals(Integer.valueOf(403), response.renderedCode);
        assertFalse(response.headers.containsKey("Access-Control-Allow-Origin"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldCompleteCorsOptionsForTrustedPasskeyOrigin() {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminCrossOriginInterceptor().doInterceptor(
                request(HttpMethod.OPTIONS, "/api/admin/account-security/passkey/registration/options",
                        Map.of("Origin", "https://admin.example.com", "Host", "admin.example.com"), "https"),
                response.response());

        assertFalse(proceed);
        assertEquals(Integer.valueOf(200), response.writeStatus);
        assertEquals("https://admin.example.com", response.headers.get("Access-Control-Allow-Origin"));
        assertEquals("POST, GET, OPTIONS, DELETE, PUT", response.headers.get("Access-Control-Allow-Methods"));
        assertEquals("Origin", response.headers.get("Vary"));
    }

    @Test
    public void shouldRejectTemporaryDbResourceWhenAdminTokenIsMissing() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        boolean proceed = new AdminTemporaryResourceInterceptor().doInterceptor(
                request(HttpMethod.GET, AdminConstants.ADMIN_DB_ATTACHED_TMP + "/image.png",
                        Map.of("Origin", "https://admin.example.com")),
                response.response());

        assertFalse(proceed);
        assertEquals(Integer.valueOf(403), response.renderedCode);
        assertEquals("https://admin.example.com", response.headers.get("Access-Control-Allow-Origin"));
    }

    private static HttpRequest request(HttpMethod httpMethod, String uri, Map<String, String> headers) {
        return request(httpMethod, uri, headers, null);
    }

    private static HttpRequest request(HttpMethod httpMethod, String uri, Map<String, String> headers, String scheme) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminCorsAndTemporaryResourceInterceptorTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMethod":
                            return httpMethod;
                        case "getUri":
                            return uri;
                        case "getHeader":
                            return headers.get(args[0].toString());
                        case "getScheme":
                            return scheme;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
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
        private Integer renderedCode;
        private Integer writeStatus;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminCorsAndTemporaryResourceInterceptorTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addHeader":
                                headers.put(args[0].toString(), args[1].toString());
                                return null;
                            case "renderCode":
                                renderedCode = (Integer) args[0];
                                return null;
                            case "write":
                                if (args.length > 1 && args[0] instanceof InputStream) {
                                    writeStatus = (Integer) args[1];
                                }
                                return null;
                            default:
                                return null;
                        }
                    });
        }
    }
}
