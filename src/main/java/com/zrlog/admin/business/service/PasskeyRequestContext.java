package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.webauthn4j.data.client.Origin;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import com.zrlog.util.ZrLogUtil;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public class PasskeyRequestContext {

    private static final int MAX_RP_ID_LENGTH = 255;
    private static final int MAX_ORIGIN_LENGTH = 512;
    private final Supplier<String> configuredSiteHostSupplier;

    public PasskeyRequestContext() {
        this(ZrLogUtil::getBlogHostByWebSite);
    }

    PasskeyRequestContext(Supplier<String> configuredSiteHostSupplier) {
        this.configuredSiteHostSupplier = Objects.requireNonNull(configuredSiteHostSupplier);
    }

    public Context resolve(HttpRequest request) {
        try {
            String originHeader = request.getHeader("Origin");
            String hostHeader = request.getHeader("Host");
            String requestScheme = Objects.toString(request.getScheme(), "").split(",", 2)[0].trim()
                    .toLowerCase(Locale.ROOT);
            if (StringUtils.isEmpty(originHeader) || StringUtils.isEmpty(hostHeader)
                    || hostHeader.contains(",") || hostHeader.contains("/") || hostHeader.contains("\\")
                    || hostHeader.contains("@") || hostHeader.contains("?") || hostHeader.contains("#")) {
                throw new PasskeyVerificationException();
            }
            URI originUri = URI.create(originHeader);
            if (StringUtils.isEmpty(originUri.getScheme()) || StringUtils.isEmpty(originUri.getHost())
                    || originUri.getUserInfo() != null || originUri.getQuery() != null || originUri.getFragment() != null
                    || (StringUtils.isNotEmpty(originUri.getPath()) && !"/".equals(originUri.getPath()))) {
                throw new PasskeyVerificationException();
            }
            Origin suppliedOrigin = new Origin(originHeader);
            String normalizedOrigin = suppliedOrigin.toString();
            String host = suppliedOrigin.getHost();
            if (StringUtils.isEmpty(host) || host.length() > MAX_RP_ID_LENGTH
                    || normalizedOrigin.length() > MAX_ORIGIN_LENGTH || isIpLiteral(host)
                    || !isValidPort(suppliedOrigin)) {
                throw new PasskeyVerificationException();
            }
            Origin requestOrigin = new Origin(requestScheme + "://" + hostHeader.trim());
            if (StringUtils.isEmpty(requestOrigin.getHost()) || !isValidPort(requestOrigin)) {
                throw new PasskeyVerificationException();
            }
            boolean sameOrigin = suppliedOrigin.equals(requestOrigin);
            if (!sameOrigin && (!isConfiguredSiteOrigin(suppliedOrigin) || !isSecureApiOrigin(requestOrigin))) {
                throw new PasskeyVerificationException();
            }
            if (!"https".equals(suppliedOrigin.getScheme())
                    && !("http".equals(suppliedOrigin.getScheme()) && isLocalHost(host))) {
                throw new PasskeyVerificationException();
            }
            return new Context(normalizedOrigin, host, suppliedOrigin.getScheme());
        } catch (PasskeyVerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PasskeyVerificationException();
        }
    }

    private boolean isConfiguredSiteOrigin(Origin suppliedOrigin) {
        String configuredHost = Objects.toString(configuredSiteHostSupplier.get(), "").trim();
        if (configuredHost.isEmpty() || configuredHost.startsWith("//")) {
            return false;
        }
        String lowerCaseConfiguredHost = configuredHost.toLowerCase(Locale.ROOT);
        String configuredUrl;
        if (lowerCaseConfiguredHost.startsWith("https://") || lowerCaseConfiguredHost.startsWith("http://")) {
            configuredUrl = configuredHost;
        } else {
            if (configuredHost.contains("://")) {
                return false;
            }
            configuredUrl = suppliedOrigin.getScheme() + "://" + configuredHost;
        }
        URI configuredUri = URI.create(configuredUrl);
        if (StringUtils.isEmpty(configuredUri.getScheme()) || StringUtils.isEmpty(configuredUri.getHost())
                || configuredUri.getUserInfo() != null || configuredUri.getQuery() != null
                || configuredUri.getFragment() != null
                || (StringUtils.isNotEmpty(configuredUri.getPath()) && !"/".equals(configuredUri.getPath()))) {
            return false;
        }
        Origin configuredOrigin = new Origin(configuredUrl);
        return isValidPort(configuredOrigin) && suppliedOrigin.equals(configuredOrigin);
    }

    private boolean isSecureApiOrigin(Origin requestOrigin) {
        return "https".equals(requestOrigin.getScheme())
                || ("http".equals(requestOrigin.getScheme()) && isLocalHost(requestOrigin.getHost()));
    }

    private boolean isValidPort(Origin origin) {
        Integer port = origin.getPort();
        return port != null && port > 0 && port <= 65535;
    }

    private boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private boolean isIpLiteral(String host) {
        String normalizedHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        if (normalizedHost.indexOf(':') >= 0 || normalizedHost.endsWith(".")) {
            return true;
        }
        String[] labels = normalizedHost.split("\\.", -1);
        return labels.length > 0 && Arrays.stream(labels).allMatch(this::isNumericAddressLabel);
    }

    private boolean isNumericAddressLabel(String label) {
        return label.matches("\\d+") || label.matches("(?i)0x[0-9a-f]+");
    }

    public static class Context {
        private final String origin;
        private final String rpId;
        private final String protocol;

        public Context(String origin, String rpId, String protocol) {
            this.origin = origin;
            this.rpId = rpId;
            this.protocol = protocol;
        }

        public String getOrigin() {
            return origin;
        }

        public String getRpId() {
            return rpId;
        }

        public String getProtocol() {
            return protocol;
        }
    }
}
