package com.zrlog.admin.web.interceptor;

import com.google.gson.Gson;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Bearer API adapter sharing the controller action bindings and account authorization. */
public final class BearerTokenInterceptor implements HandleAbleInterceptor {
    @Override public boolean isHandleAble(HttpRequest request) {
        String path = request.getUri();
        String header = request.getHeader("Authorization");
        if (header == null) return false;
        if (path.equals(WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT)) {
            // Previously issued site-wide webhook credentials remain confined to their original endpoint.
            // Legacy webhook tokens are unprefixed and share the OAuth token shape.
            // Only a currently valid legacy credential bypasses the OAuth adapter.
            return !new WebhookService().verifyToken(header.replaceFirst("(?i)^Bearer +", ""));
        }
        return path.equals("/api/admin") || path.startsWith("/api/admin/")
                || path.equals("/admin") || path.startsWith("/admin/");
    }

    @Override public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Pragma", "no-cache");
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.matches("(?i)Bearer +[^ ]+")) throw new OAuthException("invalid_token", 401);
            String token = authorization.substring(authorization.indexOf(' ') + 1).trim();
            OAuthService oauth = new OAuthService();
            boolean notification = request.getUri().equals(WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT);
            OAuthModels.Identity identity = oauth.authenticate(token, oauth.adminResource(), Set.of());
            if (!notification && !request.getUri().startsWith("/api/admin/")) throw new OAuthException("insufficient_scope", 403);
            Method method = request.getServerConfig().getRouter().getMethod(request.getUri(), request.getMethod());
            AdminTokenVO actor = new AdminTokenVO();
            actor.setUserId(identity.userId);
            actor.setAuthVersion(identity.authVersion);
            actor.setSessionId("bearer-" + OAuthService.hash(token));
            actor.setProtocol(java.net.URI.create(oauth.issuer()).getScheme());
            return AdminTokenThreadLocal.withUser(actor, () -> DelegatedAccess.withIdentity(identity, () -> {
                try { AccountPermissionService.checkRoute(method, request); }
                catch (com.zrlog.admin.business.exception.PermissionErrorException denied) {
                    throw new OAuthException("insufficient_scope", 403);
                }
                new AdminInterceptor().doMethodInterceptor(request, response, method);
                return false;
            }));
        } catch (OAuthException error) {
            response.addHeader("WWW-Authenticate", "Bearer error=\"" + error.getOAuthError() + "\"");
            response.setContentType("application/json;charset=UTF-8");
            response.write(new ByteArrayInputStream(new Gson().toJson(new OAuthModels.Error(error.getOAuthError()))
                    .getBytes(StandardCharsets.UTF_8)), error.getStatus());
            return false;
        }
    }
}
