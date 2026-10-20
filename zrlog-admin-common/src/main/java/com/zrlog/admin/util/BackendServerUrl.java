package com.zrlog.admin.util;

import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Canonical external API entry point; never add this setting to public website data.
 */
@Singleton
public final class BackendServerUrl {
    public static final String SETTING_KEY = "backend_server_url";
    private final jakarta.inject.Provider<WebSite> webSiteProvider;
    private final Supplier<Integer> listenerPort;
    private final Supplier<String> contextPath;
    private final Supplier<String> siteHost;

    @Inject
    public BackendServerUrl(
            Supplier<Integer> listenerPort,
            @Named("contextPath") Supplier<String> contextPath,
            @Named("siteHost") Supplier<String> siteHost,
            jakarta.inject.Provider<WebSite> webSiteProvider) {
        this.webSiteProvider = webSiteProvider;

        this.listenerPort = Objects.requireNonNull(listenerPort);
        this.contextPath = Objects.requireNonNull(contextPath);
        this.siteHost = Objects.requireNonNull(siteHost);
    }

    /**
     * A full backend URL already includes its externally visible context path.
     */
    public static String resolve(String backendUrl, String blogHost, String contextPath) {
        return resolve(backendUrl, blogHost, contextPath, false, null);
    }

    private static String resolve(String backendUrl, String blogHost, String contextPath,
                                  boolean allowHttp, String requestScheme) {
        String backend = Objects.toString(backendUrl, "").trim().replaceAll("/+$", "");
        String context = Objects.toString(contextPath, "").replaceAll("/+$", "");
        if (!backend.isEmpty()) {
            String address = normalize(backend, allowHttp);
            return normalize(address + (URI.create(address).getRawPath().isEmpty() ? context : ""), allowHttp);
        }
        String host = Objects.toString(blogHost, "").trim().replaceAll("/+$", "");
        if (!host.contains("://")) {
            URI parsed = URI.create("https://" + host);
            boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(parsed.getHost(), ""));
            String scheme = allowHttp ? requestScheme : (local ? "http" : "https");
            host = scheme + "://" + host;
        }
        return normalize(host + context, allowHttp);
    }

    private static boolean sameOrigin(URI expected, URI supplied) {
        return expected.getScheme().equalsIgnoreCase(supplied.getScheme())
                && expected.getHost().equalsIgnoreCase(supplied.getHost())
                && originPort(expected) == originPort(supplied);
    }

    private static int originPort(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }

    public static String normalize(String value) {
        return normalize(value, false);
    }

    private static String normalize(String value, boolean allowHttp) {
        if (value == null) return null;
        String address = value.trim();
        if (address.isEmpty()) return "";
        URI uri = URI.create(address);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean local = host != null && Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(scheme) || (allowHttp || local) && "http".equals(scheme))
                || uri.getPort() == 0 || uri.getPort() > 65535 || address.length() > 2048) {
            throw new IllegalArgumentException(SETTING_KEY);
        }
        String path = uri.getPath();
        if (path.contains("\\") || path.contains("//") || path.codePoints().anyMatch(c -> c <= 32 || c == 127)
                || uri.getRawPath().matches("(?i).*%(2f|5c|25).*")) throw new IllegalArgumentException(SETTING_KEY);
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException(SETTING_KEY);
        }
        return scheme + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT)
                + uri.getRawPath().replaceAll("/+$", "");
    }

    public String configured() {
        return configured(false, null);
    }

    private String configured(boolean allowHttp, String requestScheme) {
        String backend = this.webSiteProvider.get().getStringValueByName(SETTING_KEY);
        if (backend == null || backend.isBlank()) backend = System.getenv("ZRLOG_BACKEND_URL");
        if (backend == null || backend.isBlank()) backend = System.getenv("DEFAULT_BACKEND_SERVER_URL");
        String host = siteHost.get();
        if (host == null || host.isBlank()) {
            // A fresh local installation has no canonical host; use the configured listener, never request headers.
            Integer port = listenerPort.get();
            if (port != null && port > 0 && port <= 65535) {
                host = "localhost" + (port == 80 ? "" : ":" + port);
            }
        }
        return resolve(backend, host, contextPath.get(), allowHttp, requestScheme);
    }

    /**
     * Browser CSRF checks support ordinary HTTP deployments, independently of OAuth URL policy.
     */
    public void requireAdminRequestOrigin(com.hibegin.http.server.api.HttpRequest request) {
        String scheme = Objects.toString(request.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme)) throw new IllegalArgumentException("Origin");
        requireAdminOrigin(request.getHeader("Origin"), configured(true, scheme), true);
    }

    public void requireAdminOrigin(String origin, String backendUrl) {
        requireAdminOrigin(origin, backendUrl, false);
    }

    private void requireAdminOrigin(String origin, String backendUrl, boolean allowHttp) {
        URI supplied = URI.create(normalize(Objects.toString(origin, ""), allowHttp));
        if (supplied.getHost() == null) throw new IllegalArgumentException("Origin");
        if (!(supplied.getRawPath().isEmpty() || supplied.getRawPath().equals("/"))
                || supplied.getRawQuery() != null || supplied.getPort() == 0 || supplied.getPort() > 65535)
            throw new IllegalArgumentException("Origin");
        if (sameOrigin(URI.create(backendUrl), supplied)) return;

        String configured =
                this.webSiteProvider.get().getStringValueByName("admin_static_resource_base_url");
        if (configured != null && !configured.isBlank()) {
            try {
                String address = normalize(configured, allowHttp);
                if (sameOrigin(URI.create(address), supplied)) return;
            } catch (IllegalArgumentException invalidConfiguration) {
                // A malformed frontend URL must not add a trusted origin.
            }
        }
        throw new IllegalArgumentException("Origin");
    }
}
