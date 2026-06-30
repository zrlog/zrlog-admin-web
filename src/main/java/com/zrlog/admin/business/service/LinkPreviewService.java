package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.LinkPreviewResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LinkPreviewService {

    private static final Logger LOGGER = LoggerUtil.getLogger(LinkPreviewService.class);
    private static final String CACHE_KEY = "link_preview";
    private static final int MAX_CACHE_SIZE = 200;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_HTML_BYTES = 256 * 1024;
    private static final int MAX_URL_LENGTH = 2048;
    private static final long CACHE_TTL_MILLIS = Duration.ofHours(6).toMillis();
    private static final long EMPTY_CACHE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final HttpSender httpSender;

    public LinkPreviewService() {
        this(defaultHttpSender());
    }

    LinkPreviewService(HttpSender httpSender) {
        this.httpSender = httpSender;
    }

    private static HttpSender defaultHttpSender() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return request -> client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    public LinkPreviewResponse fetch(String rawUrl, String userAgent) {
        LinkPreviewResponse emptyResponse = emptyResponse(rawUrl);
        Optional<URI> safeUri = normalizeSafeUri(rawUrl);
        if (safeUri.isEmpty()) {
            return emptyResponse;
        }
        String cacheKey = safeUri.get().toString();
        long now = System.currentTimeMillis();
        LinkPreviewCacheEntry cached = getCached(cacheKey, now);
        if (cached != null) {
            return cached.getResponse();
        }
        LinkPreviewResponse response = fetchUncached(safeUri.get(), 0, userAgent);
        putCached(cacheKey, response, now + (response.isAvailable() ? CACHE_TTL_MILLIS : EMPTY_CACHE_TTL_MILLIS), now);
        return response;
    }

    private synchronized LinkPreviewCacheEntry getCached(String cacheKey, long now) {
        ArrayList<LinkPreviewCacheEntry> entries = readCache(now);
        for (LinkPreviewCacheEntry entry : entries) {
            if (cacheKey.equals(entry.getUrl()) && entry.getExpiresAt() > now) {
                entry.setAccessedAt(now);
                writeCache(entries);
                return entry;
            }
        }
        return null;
    }

    private synchronized void putCached(String cacheKey, LinkPreviewResponse response, long expiresAt, long now) {
        ArrayList<LinkPreviewCacheEntry> entries = readCache(now);
        entries.removeIf(entry -> cacheKey.equals(entry.getUrl()));
        entries.add(new LinkPreviewCacheEntry(cacheKey, response, expiresAt, now));
        writeCache(entries);
    }

    private ArrayList<LinkPreviewCacheEntry> readCache(long now) {
        ArrayList<LinkPreviewCacheEntry> entries = new ArrayList<>();
        try {
            LinkPreviewCacheEntry[] cachedEntries = new WebsiteCacheService().getJson(CACHE_KEY, LinkPreviewCacheEntry[].class);
            if (cachedEntries == null) {
                return entries;
            }
            for (LinkPreviewCacheEntry entry : cachedEntries) {
                if (entry != null && entry.getResponse() != null && StringUtils.isNotEmpty(entry.getUrl())
                        && entry.getExpiresAt() > now) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read link preview cache failed", e);
        }
        return entries;
    }

    private void writeCache(ArrayList<LinkPreviewCacheEntry> entries) {
        entries.sort(Comparator.comparingLong(LinkPreviewCacheEntry::getAccessedAt).reversed());
        if (entries.size() > MAX_CACHE_SIZE) {
            entries.subList(MAX_CACHE_SIZE, entries.size()).clear();
        }
        new WebsiteCacheService().putJson(CACHE_KEY, entries);
    }

    private LinkPreviewResponse fetchUncached(URI uri, int redirects, String userAgent) {
        LinkPreviewResponse response = emptyResponse(uri.toString());
        response.setDomain(uri.getHost());
        try {
            if (!isSafeHost(uri.getHost())) {
                return response;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("User-Agent", normalizeUserAgent(userAgent))
                    .GET()
                    .build();
            HttpResponse<InputStream> httpResponse = httpSender.send(request);
            int statusCode = httpResponse.statusCode();
            if (statusCode >= 300 && statusCode < 400) {
                if (redirects >= MAX_REDIRECTS) {
                    closeQuietly(httpResponse.body());
                    return response;
                }
                Optional<String> location = httpResponse.headers().firstValue("location");
                if (location.isEmpty()) {
                    closeQuietly(httpResponse.body());
                    return response;
                }
                Optional<URI> redirectUri = normalizeSafeUri(uri.resolve(location.get()).toString());
                closeQuietly(httpResponse.body());
                if (redirectUri.isEmpty()) {
                    return response;
                }
                return fetchUncached(redirectUri.get(), redirects + 1, userAgent);
            }
            if (statusCode < 200 || statusCode >= 300) {
                closeQuietly(httpResponse.body());
                return response;
            }
            String contentType = httpResponse.headers().firstValue("content-type").orElse("");
            if (StringUtils.isNotEmpty(contentType) && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                closeQuietly(httpResponse.body());
                return response;
            }
            String html = readLimited(httpResponse.body());
            if (StringUtils.isEmpty(html)) {
                return response;
            }
            return parse(uri, html);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Fetch link preview failed: " + uri, e);
            return response;
        }
    }

    private String normalizeUserAgent(String userAgent) {
        if (StringUtils.isEmpty(userAgent)) {
            return BROWSER_USER_AGENT;
        }
        String trimmed = userAgent.trim().replace("\r", "").replace("\n", "");
        if (trimmed.length() > 512) {
            return trimmed.substring(0, 512);
        }
        return trimmed;
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private LinkPreviewResponse parse(URI uri, String html) {
        Document document = Jsoup.parse(html, uri.toString());
        LinkPreviewResponse response = emptyResponse(uri.toString());
        response.setDomain(uri.getHost());
        response.setTitle(firstNonEmpty(
                attr(document, "meta[property=og:title]", "content"),
                attr(document, "meta[name=twitter:title]", "content"),
                document.title()
        ));
        response.setDescription(firstNonEmpty(
                attr(document, "meta[property=og:description]", "content"),
                attr(document, "meta[name=twitter:description]", "content"),
                attr(document, "meta[name=description]", "content")
        ));
        response.setImage(resolveUri(uri, firstNonEmpty(
                attr(document, "meta[property=og:image]", "content"),
                attr(document, "meta[name=twitter:image]", "content"),
                attr(document, "meta[property=twitter:image]", "content"),
                favicon(document, uri)
        )));
        response.setSiteName(firstNonEmpty(
                attr(document, "meta[property=og:site_name]", "content"),
                attr(document, "meta[name=application-name]", "content"),
                uri.getHost()
        ));
        response.setAvailable(StringUtils.isNotEmpty(response.getTitle())
                || StringUtils.isNotEmpty(response.getDescription())
                || StringUtils.isNotEmpty(response.getImage()));
        return response;
    }

    private Optional<URI> normalizeSafeUri(String rawUrl) {
        if (StringUtils.isEmpty(rawUrl) || rawUrl.length() > MAX_URL_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(rawUrl.trim()).normalize();
            String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return Optional.empty();
            }
            if (StringUtils.isEmpty(uri.getHost()) || StringUtils.isNotEmpty(uri.getUserInfo())) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    boolean isSafeHost(String host) throws IOException {
        if (StringUtils.isEmpty(host)) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            return false;
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
            byte[] bytes = address.getAddress();
            if (bytes.length == 4 && isBlockedIpv4(bytes)) {
                return false;
            }
            if (bytes.length == 16 && isBlockedIpv6(bytes)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlockedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 100 && second >= 64 && second <= 127)
                || first >= 224;
    }

    private boolean isBlockedIpv6(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || isIpv6Loopback(bytes)
                || isIpv6Unspecified(bytes);
    }

    private boolean isIpv6Loopback(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[15] == 1;
    }

    private boolean isIpv6Unspecified(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private String readLimited(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_HTML_BYTES) {
                    break;
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private String resolveUri(URI baseUri, String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        try {
            URI uri = baseUri.resolve(value.trim()).normalize();
            String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            return uri.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String attr(Document document, String selector, String attr) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return "";
        }
        return element.attr(attr).trim();
    }

    private String favicon(Document document, URI uri) {
        String favicon = "";
        String appleTouchIcon = "";
        for (Element element : document.select("link[rel][href]")) {
            String rel = element.attr("rel").toLowerCase(Locale.ROOT);
            String href = element.attr("href").trim();
            if (StringUtils.isEmpty(href)) {
                continue;
            }
            if (rel.contains("apple-touch-icon") && StringUtils.isEmpty(appleTouchIcon)) {
                appleTouchIcon = href;
            }
            if (rel.contains("icon") && StringUtils.isEmpty(favicon)) {
                favicon = href;
            }
        }
        String icon = firstNonEmpty(appleTouchIcon, favicon);
        if (StringUtils.isNotEmpty(icon)) {
            return icon;
        }
        return uri.getScheme() + "://" + uri.getAuthority() + "/favicon.ico";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LinkPreviewResponse emptyResponse(String url) {
        LinkPreviewResponse response = new LinkPreviewResponse();
        response.setUrl(Objects.requireNonNullElse(url, ""));
        response.setTitle("");
        response.setDescription("");
        response.setImage("");
        response.setSiteName("");
        response.setDomain("");
        response.setAvailable(false);
        return response;
    }

    public static class LinkPreviewCacheEntry {
        private String url;
        private LinkPreviewResponse response;
        private long expiresAt;
        private long accessedAt;

        public LinkPreviewCacheEntry() {
        }

        private LinkPreviewCacheEntry(String url, LinkPreviewResponse response, long expiresAt, long accessedAt) {
            this.url = url;
            this.response = response;
            this.expiresAt = expiresAt;
            this.accessedAt = accessedAt;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public LinkPreviewResponse getResponse() {
            return response;
        }

        public void setResponse(LinkPreviewResponse response) {
            this.response = response;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public long getAccessedAt() {
            return accessedAt;
        }

        public void setAccessedAt(long accessedAt) {
            this.accessedAt = accessedAt;
        }
    }

    @FunctionalInterface
    interface HttpSender {
        HttpResponse<InputStream> send(HttpRequest request) throws IOException, InterruptedException;
    }
}
