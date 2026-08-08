package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.webauthn4j.data.client.Origin;
import com.zrlog.admin.business.exception.PasskeyVerificationException;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public class PasskeyRequestContext {

    private static final int MAX_RP_ID_LENGTH = 255;
    private static final int MAX_ORIGIN_LENGTH = 512;

    public Context resolve(HttpRequest request) {
        try {
            String originHeader = request.getHeader("Origin");
            String hostHeader = request.getHeader("Host");
            if (StringUtils.isEmpty(originHeader) || StringUtils.isEmpty(hostHeader)
                    || hostHeader.contains(",") || hostHeader.contains("/") || hostHeader.contains("\\")) {
                throw new PasskeyVerificationException();
            }
            URI originUri = URI.create(originHeader);
            if (StringUtils.isEmpty(originUri.getScheme()) || StringUtils.isEmpty(originUri.getHost())
                    || originUri.getUserInfo() != null || originUri.getQuery() != null || originUri.getFragment() != null
                    || (StringUtils.isNotEmpty(originUri.getPath()) && !"/".equals(originUri.getPath()))) {
                throw new PasskeyVerificationException();
            }
            String requestScheme = Objects.toString(request.getScheme(), "").split(",", 2)[0].trim()
                    .toLowerCase(Locale.ROOT);
            Origin suppliedOrigin = new Origin(originHeader);
            String normalizedOrigin = suppliedOrigin.toString();
            String host = suppliedOrigin.getHost();
            if (StringUtils.isEmpty(host) || host.length() > MAX_RP_ID_LENGTH
                    || normalizedOrigin.length() > MAX_ORIGIN_LENGTH || isIpLiteral(host)) {
                throw new PasskeyVerificationException();
            }
            Origin requestOrigin = new Origin(requestScheme + "://" + hostHeader.trim());
            if (!suppliedOrigin.equals(requestOrigin)) {
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

    private boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private boolean isIpLiteral(String host) {
        String normalizedHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        return normalizedHost.indexOf(':') >= 0 || normalizedHost.matches("(?:\\d{1,3}\\.){3}\\d{1,3}");
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
