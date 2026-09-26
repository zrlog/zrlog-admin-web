package com.zrlog.admin.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Canonical external API entry point; never add this setting to public website data. */
public final class BackendServerUrl {
    public static final String SETTING_KEY = "backend_server_url";

    private BackendServerUrl() { }

    public static String normalize(String value) {
        if (value == null) return null;
        String address = value.trim();
        if (address.isEmpty()) return "";
        URI uri = URI.create(address);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean local = host != null && Set.of("localhost", "127.0.0.1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(scheme) || local && "http".equals(scheme))
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
}
