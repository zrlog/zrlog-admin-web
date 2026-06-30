package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.LinkPreviewResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LinkPreviewServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldNormalizeOnlyHttpAndHttpsUrlsWithoutUserInfo() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("normalizeSafeUri", String.class);

        Optional<URI> normalized = (Optional<URI>) method.invoke(service, " https://example.com/a/../b?q=1 ");

        assertTrue(normalized.isPresent());
        assertEquals("https://example.com/b?q=1", normalized.get().toString());
        assertTrue(((Optional<URI>) method.invoke(service, "http://example.com")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "ftp://example.com/file")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "https://user@example.com")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "https:///missing-host")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "https://exa mple.com")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "")).isPresent());
        assertFalse(((Optional<URI>) method.invoke(service, "https://example.com/" + "a".repeat(2049))).isPresent());
    }

    @Test
    public void shouldParseOpenGraphAndTwitterMetadata() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("parse", URI.class, String.class);
        String html = "<html><head>"
                + "<meta property=\"og:title\" content=\"OG Title\">"
                + "<meta property=\"og:description\" content=\"OG Description\">"
                + "<meta name=\"twitter:image\" content=\"/img/card.png\">"
                + "<meta property=\"og:site_name\" content=\"Example Site\">"
                + "</head><body>content</body></html>";

        LinkPreviewResponse response = (LinkPreviewResponse) method.invoke(service,
                URI.create("https://example.com/posts/1"), html);

        assertTrue(response.isAvailable());
        assertEquals("https://example.com/posts/1", response.getUrl());
        assertEquals("example.com", response.getDomain());
        assertEquals("OG Title", response.getTitle());
        assertEquals("OG Description", response.getDescription());
        assertEquals("https://example.com/img/card.png", response.getImage());
        assertEquals("Example Site", response.getSiteName());
    }

    @Test
    public void shouldParseFallbackTitleDescriptionAndFavicon() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("parse", URI.class, String.class);
        String html = "<html><head>"
                + "<title>Document Title</title>"
                + "<meta name=\"description\" content=\"Fallback description\">"
                + "<link rel=\"shortcut icon\" href=\"../favicon.ico\">"
                + "</head></html>";

        LinkPreviewResponse response = (LinkPreviewResponse) method.invoke(service,
                URI.create("https://example.com/posts/1"), html);

        assertEquals("Document Title", response.getTitle());
        assertEquals("Fallback description", response.getDescription());
        assertEquals("https://example.com/favicon.ico", response.getImage());
        assertEquals("example.com", response.getSiteName());
    }

    @Test
    public void shouldPreferAppleTouchIconAndSkipEmptyIconLinks() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("parse", URI.class, String.class);
        String html = "<html><head>"
                + "<title>Icon Page</title>"
                + "<link rel=\"icon\" href=\"\">"
                + "<link rel=\"apple-touch-icon\" href=\"/apple.png\">"
                + "<link rel=\"shortcut icon\" href=\"/favicon.png\">"
                + "</head></html>";

        LinkPreviewResponse response = (LinkPreviewResponse) method.invoke(service,
                URI.create("https://example.com/posts/1"), html);

        assertEquals("Icon Page", response.getTitle());
        assertEquals("https://example.com/apple.png", response.getImage());
    }

    @Test
    public void shouldCleanUserAgentAndFallbackWhenMissing() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("normalizeUserAgent", String.class);

        String fallback = (String) method.invoke(service, new Object[]{null});
        String cleaned = (String) method.invoke(service, " Browser\r\nInjected");
        String trimmed = (String) method.invoke(service, "a".repeat(600));

        assertTrue(fallback.contains("Mozilla/5.0"));
        assertEquals("BrowserInjected", cleaned);
        assertEquals(512, trimmed.length());
    }

    @Test
    public void shouldResolveOnlyHttpImageUris() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("resolveUri", URI.class, String.class);
        URI base = URI.create("https://example.com/post/page.html");

        assertEquals("https://example.com/assets/card.png", method.invoke(service, base, "../assets/card.png"));
        assertEquals("https://cdn.example.com/card.png", method.invoke(service, base, "//cdn.example.com/card.png"));
        assertEquals("", method.invoke(service, base, "javascript:alert(1)"));
        assertEquals("", method.invoke(service, base, "mailto:test@example.com"));
        assertEquals("", method.invoke(service, base, "http://[bad"));
        assertEquals("", method.invoke(service, base, ""));
    }

    @Test
    public void shouldRejectUnsafeHostsWithoutNetworkRequest() throws Exception {
        LinkPreviewService service = new LinkPreviewService();

        assertFalse(service.isSafeHost(""));
        assertFalse(service.isSafeHost("localhost"));
        assertFalse(service.isSafeHost("app.localhost"));
    }

    @Test
    public void shouldDetectBlockedIpAddressRanges() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method ipv4 = method("isBlockedIpv4", byte[].class);
        Method ipv6 = method("isBlockedIpv6", byte[].class);

        assertTrue((Boolean) ipv4.invoke(service, new Object[]{ipv4(10, 0, 0, 1)}));
        assertTrue((Boolean) ipv4.invoke(service, new Object[]{ipv4(100, 64, 0, 1)}));
        assertTrue((Boolean) ipv4.invoke(service, new Object[]{ipv4(192, 168, 1, 1)}));
        assertTrue((Boolean) ipv4.invoke(service, new Object[]{ipv4(224, 0, 0, 1)}));
        assertFalse((Boolean) ipv4.invoke(service, new Object[]{ipv4(8, 8, 8, 8)}));

        assertTrue((Boolean) ipv6.invoke(service, new Object[]{ipv6(0, 0, 0, 0, 0, 0, 0, 1)}));
        assertTrue((Boolean) ipv6.invoke(service, new Object[]{ipv6(0, 0, 0, 0, 0, 0, 0, 0)}));
        assertTrue((Boolean) ipv6.invoke(service, new Object[]{ipv6(0xfc00, 0, 0, 0, 0, 0, 0, 1)}));
        assertTrue((Boolean) ipv6.invoke(service, new Object[]{ipv6(0xfe80, 0, 0, 0, 0, 0, 0, 1)}));
        assertFalse((Boolean) ipv6.invoke(service, new Object[]{ipv6(0x2001, 0x4860, 0x4860, 0, 0, 0, 0, 0x8888)}));
    }

    @Test
    public void shouldReadHtmlBodyWithinConfiguredLimit() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("readLimited", java.io.InputStream.class);
        String html = "<html>" + "a".repeat(20_000) + "</html>";

        String result = (String) method.invoke(service,
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));

        assertEquals(html, result);
    }

    @Test
    public void shouldStopReadingHtmlBodyAfterConfiguredLimit() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("readLimited", java.io.InputStream.class);
        String html = "<html>" + "a".repeat(300_000) + "</html>";

        String result = (String) method.invoke(service,
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));

        assertTrue(result.length() < html.length());
    }

    @Test
    public void shouldReturnEmptyPreviewForInvalidUrlAndUnsafeHost() {
        AtomicInteger calls = new AtomicInteger();
        LinkPreviewService invalidUrlService = hostAllowedService(request -> {
            calls.incrementAndGet();
            return htmlResponse(request.uri(), "<html></html>");
        });
        LinkPreviewService unsafeHostService = new LinkPreviewService(request -> {
            calls.incrementAndGet();
            return htmlResponse(request.uri(), "<html></html>");
        }) {
            @Override
            boolean isSafeHost(String host) {
                return false;
            }
        };

        LinkPreviewResponse invalidUrl = invalidUrlService.fetch("javascript:alert(1)", null);
        LinkPreviewResponse unsafeHost = unsafeHostService.fetch("https://example.com/post", null);

        assertFalse(invalidUrl.isAvailable());
        assertEquals("javascript:alert(1)", invalidUrl.getUrl());
        assertFalse(unsafeHost.isAvailable());
        assertEquals("example.com", unsafeHost.getDomain());
        assertEquals(0, calls.get());
    }

    @Test
    public void shouldFetchPreviewAndReuseWebsiteCacheThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AtomicInteger calls = new AtomicInteger();
            LinkPreviewService service = hostAllowedService(request -> {
                calls.incrementAndGet();
                return htmlResponse(request.uri(), "<html><head>"
                        + "<meta property=\"og:title\" content=\"Cached Title\">"
                        + "<meta name=\"description\" content=\"Cached description\">"
                        + "</head></html>");
            });

            LinkPreviewResponse first = service.fetch("https://example.com/post", "Test\r\nAgent");
            LinkPreviewResponse second = service.fetch("https://example.com/post", "Other Agent");
            Map<String, Object> cacheRow = db.queryOne("select value from website where name=?",
                    "admin_cache:link_preview");

            assertTrue(first.isAvailable());
            assertEquals("Cached Title", first.getTitle());
            assertEquals("Cached description", first.getDescription());
            assertEquals("Cached Title", second.getTitle());
            assertEquals(1, calls.get());
            assertNotNull(cacheRow);
            assertTrue(String.valueOf(cacheRow.get("value")).contains("Cached Title"));
        }
    }

    @Test
    public void shouldReturnEmptyPreviewForRedirectAndHttpFailureEdges() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            LinkPreviewService service = hostAllowedService(request -> {
                String path = request.uri().getPath();
                if ("/too-many".equals(path)) {
                    return response(request.uri(), 302, Map.of("location", List.of("/too-many")), "");
                }
                if ("/missing-location".equals(path)) {
                    return response(request.uri(), 302, Map.of(), "");
                }
                if ("/bad-redirect".equals(path)) {
                    return response(request.uri(), 302, Map.of("location", List.of("ftp://example.com/file")), "");
                }
                if ("/server-error".equals(path)) {
                    return response(request.uri(), 500, Map.of("content-type", List.of("text/html")), "error");
                }
                if ("/empty".equals(path)) {
                    return response(request.uri(), 200, Map.of("content-type", List.of("text/html")), "");
                }
                if ("/boom".equals(path)) {
                    throw new IOException("boom");
                }
                return htmlResponse(request.uri(), "<html><head><title>Unexpected</title></head></html>");
            });

            assertFalse(service.fetch("https://example.com/too-many", null).isAvailable());
            assertFalse(service.fetch("https://example.com/missing-location", null).isAvailable());
            assertFalse(service.fetch("https://example.com/bad-redirect", null).isAvailable());
            assertFalse(service.fetch("https://example.com/server-error", null).isAvailable());
            assertFalse(service.fetch("https://example.com/empty", null).isAvailable());
            assertFalse(service.fetch("https://example.com/boom", null).isAvailable());
        }
    }

    @Test
    public void shouldFollowSafeRedirectBeforeParsingPreview() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AtomicInteger calls = new AtomicInteger();
            LinkPreviewService service = hostAllowedService(request -> {
                calls.incrementAndGet();
                if ("/redirect".equals(request.uri().getPath())) {
                    return response(request.uri(), 302, Map.of("location", List.of("/final")), "");
                }
                return htmlResponse(request.uri(), "<html><head><title>Final Page</title></head></html>");
            });

            LinkPreviewResponse response = service.fetch("https://example.com/redirect", null);

            assertTrue(response.isAvailable());
            assertEquals("https://example.com/final", response.getUrl());
            assertEquals("Final Page", response.getTitle());
            assertEquals(2, calls.get());
        }
    }

    @Test
    public void shouldCacheUnavailablePreviewForNonHtmlResponse() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AtomicInteger calls = new AtomicInteger();
            LinkPreviewService service = hostAllowedService(request -> {
                calls.incrementAndGet();
                return response(request.uri(), 200, Map.of("content-type", List.of("application/json")), "{}");
            });

            LinkPreviewResponse first = service.fetch("https://example.com/api", null);
            LinkPreviewResponse second = service.fetch("https://example.com/api", null);

            assertFalse(first.isAvailable());
            assertEquals("example.com", first.getDomain());
            assertFalse(second.isAvailable());
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void shouldBuildEmptyResponseContract() throws Exception {
        LinkPreviewService service = new LinkPreviewService();
        Method method = method("emptyResponse", String.class);

        LinkPreviewResponse response = (LinkPreviewResponse) method.invoke(service, "https://example.com");

        assertEquals("https://example.com", response.getUrl());
        assertEquals("", response.getTitle());
        assertEquals("", response.getDescription());
        assertEquals("", response.getImage());
        assertEquals("", response.getSiteName());
        assertEquals("", response.getDomain());
        assertFalse(response.isAvailable());
    }

    @Test
    public void shouldExposeLinkPreviewCacheEntryAccessors() {
        LinkPreviewService.LinkPreviewCacheEntry entry = new LinkPreviewService.LinkPreviewCacheEntry();
        LinkPreviewResponse response = new LinkPreviewResponse();

        entry.setUrl("https://example.com");
        entry.setResponse(response);
        entry.setExpiresAt(11L);
        entry.setAccessedAt(7L);

        assertEquals("https://example.com", entry.getUrl());
        assertEquals(response, entry.getResponse());
        assertEquals(11L, entry.getExpiresAt());
        assertEquals(7L, entry.getAccessedAt());
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = LinkPreviewService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static LinkPreviewService hostAllowedService(LinkPreviewService.HttpSender sender) {
        return new LinkPreviewService(sender) {
            @Override
            boolean isSafeHost(String host) {
                return true;
            }
        };
    }

    private static HttpResponse<java.io.InputStream> htmlResponse(URI uri, String html) {
        return response(uri, 200, Map.of("content-type", List.of("text/html; charset=utf-8")), html);
    }

    private static HttpResponse<java.io.InputStream> response(URI uri, int statusCode,
                                                             Map<String, List<String>> headers, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(uri).build();
            }

            @Override
            public Optional<HttpResponse<java.io.InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public java.io.InputStream body() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static byte[] ipv4(int a, int b, int c, int d) {
        return new byte[]{(byte) a, (byte) b, (byte) c, (byte) d};
    }

    private static byte[] ipv6(int a, int b, int c, int d, int e, int f, int g, int h) {
        byte[] bytes = new byte[16];
        int[] groups = {a, b, c, d, e, f, g, h};
        for (int i = 0; i < groups.length; i++) {
            bytes[i * 2] = (byte) ((groups[i] >> 8) & 0xff);
            bytes[i * 2 + 1] = (byte) (groups[i] & 0xff);
        }
        return bytes;
    }
}
