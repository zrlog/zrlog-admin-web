package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.io.LengthByteArrayInputStream;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import com.zrlog.admin.business.service.PasskeyRequestContext;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.common.Constants;
import com.zrlog.util.CrossUtils;

import java.util.Objects;

/**
 * 支持自动处理后台的跨域请求
 */
public class AdminCrossOriginInterceptor extends AdminInterceptor {

    private static final String ADMIN_API_BASE_PATH = "/api" + AdminConstants.ADMIN_URI_BASE_PATH;
    private static final String PUBLIC_PASSKEY_API_PATH_PREFIX = ADMIN_API_BASE_PATH + "/passkey/";
    private static final String ACCOUNT_PASSKEY_API_PATH_PREFIX = ADMIN_API_BASE_PATH + "/account-security/passkey";
    private final PasskeyRequestContext passkeyRequestContext = new PasskeyRequestContext();

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        boolean passkeyRequest = isPasskeyRequest(request.getUri());
        if (passkeyRequest || Constants.API_PUBLIC_ADMIN_RESOURCE.equals(request.getUri())) {
            response.addHeader("Vary", "Origin");
        }
        if (!CrossUtils.isEnableOrigin(request)) {
            return true;
        }
        String origin = request.getHeader("Origin");
        if (Objects.isNull(origin)) {
            return true;
        }
        if (passkeyRequest && !isTrustedPasskeyOrigin(request)) {
            if (request.getMethod() == HttpMethod.OPTIONS) {
                response.renderCode(403);
                return false;
            }
            return true;
        }
        response.addHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, " + AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER);
        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
            response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With, " + AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER);
            response.write(new LengthByteArrayInputStream(new byte[0]), 200);
            return false;
        }
        return true;
    }

    private boolean isPasskeyRequest(String uri) {
        return uri != null && (uri.startsWith(PUBLIC_PASSKEY_API_PATH_PREFIX)
                || uri.startsWith(ACCOUNT_PASSKEY_API_PATH_PREFIX));
    }

    private boolean isTrustedPasskeyOrigin(HttpRequest request) {
        try {
            passkeyRequestContext.resolve(request);
            return true;
        } catch (PasskeyVerificationException e) {
            return false;
        }
    }
}
