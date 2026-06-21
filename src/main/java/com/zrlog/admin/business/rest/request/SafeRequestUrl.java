package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.exception.ArgsException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class SafeRequestUrl {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto", "tel");

    private SafeRequestUrl() {
    }

    static String normalize(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (StringUtils.isNotEmpty(scheme) && !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                throw new ArgsException("url");
            }
            if (requiresHost(uri) && StringUtils.isEmpty(uri.getHost())) {
                throw new ArgsException("url");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new ArgsException("url");
        }
    }

    private static boolean requiresHost(URI uri) {
        if (StringUtils.isEmpty(uri.getScheme())) {
            return uri.toString().startsWith("//");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }
}
