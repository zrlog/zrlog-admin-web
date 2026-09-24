package com.zrlog.admin.web.interceptor;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.business.service.OAuthService;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public OAuth protocol endpoints intentionally use standard OAuth HTTP errors, without admin envelopes. */
public class OAuthInterceptor implements HandleAbleInterceptor {
    @Override public boolean isHandleAble(HttpRequest request) {
        String path = request.getUri();
        return path.startsWith("/oauth/") || path.startsWith("/.well-known/oauth-") || path.equals("/api/oauth/me");
    }
    @Override public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        OAuthService service = new OAuthService();
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Referrer-Policy", "no-referrer");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        if (request.getMethod() == HttpMethod.OPTIONS) { response.renderCode(204); return false; }
        try {
            String path = request.getUri();
            if (path.equals("/.well-known/oauth-authorization-server" + java.net.URI.create(service.issuer()).getRawPath()) && request.getMethod() == HttpMethod.GET) {
                json(response, service.metadata(), 200);
            } else if (path.equals(java.net.URI.create(service.resourceMetadataUrl()).getRawPath()) && request.getMethod() == HttpMethod.GET) {
                json(response, service.resourceMetadata(), 200);
            } else if (path.equals("/oauth/authorize") && request.getMethod() == HttpMethod.GET) {
                Map<String,String> p = parameters(Objects.toString(request.getQueryStr(), ""));
                OAuthModels.AuthorizationRequest a = new OAuthModels.AuthorizationRequest();
                a.client_id=p.get("client_id"); a.redirect_uri=p.get("redirect_uri"); a.response_type=p.get("response_type");
                a.scope=p.get("scope"); a.state=p.get("state"); a.code_challenge=p.get("code_challenge");
                a.code_challenge_method=p.get("code_challenge_method"); a.resource=p.get("resource");
                response.redirect(service.authorize(a));
            } else if ((path.equals("/oauth/token") || path.equals("/oauth/revoke")) && request.getMethod() == HttpMethod.POST) {
                String contentType = Objects.toString(request.getHeader("Content-Type"), "");
                if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded") || !Objects.toString(request.getQueryStr(), "").isEmpty()) throw new OAuthException("invalid_request");
                ByteBuffer buffer = request.getRequestBodyByteBuffer();
                if (buffer == null || buffer.remaining() > 16384) throw new OAuthException("invalid_request");
                Map<String,String> p = parameters(StandardCharsets.UTF_8.decode(buffer.asReadOnlyBuffer()).toString());
                if (request.getHeader("Authorization") != null) throw new OAuthException("invalid_client", 401);
                if (path.equals("/oauth/revoke")) { service.revoke(p.get("token"), p.get("client_id")); json(response, new Object(), 200); }
                else {
                    OAuthModels.TokenRequest t = new OAuthModels.TokenRequest();
                    t.grant_type=p.get("grant_type"); t.client_id=p.get("client_id"); t.code=p.get("code"); t.redirect_uri=p.get("redirect_uri");
                    t.code_verifier=p.get("code_verifier"); t.refresh_token=p.get("refresh_token"); t.scope=p.get("scope"); t.resource=p.get("resource");
                    json(response, service.token(t), 200);
                }
            } else if (path.equals("/api/oauth/me") && request.getMethod() == HttpMethod.GET) {
                String auth = request.getHeader("Authorization");
                if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) throw new OAuthException("invalid_token", 401);
                json(response, service.authenticate(auth.substring(7), service.resource(), Set.of("articles:read")), 200);
            } else { response.renderCode(405); }
        } catch (OAuthException e) {
            if (e.getStatus() == 401 || e.getStatus() == 403) response.addHeader("WWW-Authenticate", "Bearer error=\"" + e.getOAuthError() + "\", resource_metadata=\"" + service.resourceMetadataUrl() + "\", scope=\"articles:read\"");
            json(response, new OAuthModels.Error(e.getOAuthError()), e.getStatus());
        }
        return false;
    }
    public static Map<String,String> parameters(String encoded) {
        if (encoded.length() > 16384) throw new OAuthException("invalid_request");
        Map<String,String> result = new HashMap<>();
        try {
            for (String part : encoded.split("&")) {
                if (part.isEmpty()) continue;
                String[] pair = part.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                if (result.putIfAbsent(key, value) != null) throw new OAuthException("invalid_request");
            }
        } catch (IllegalArgumentException e) { throw new OAuthException("invalid_request"); }
        return result;
    }
    private static void json(HttpResponse response, Object data, int status) {
        response.setContentType("application/json;charset=UTF-8");
        response.write(new ByteArrayInputStream(new Gson().toJson(data).getBytes(StandardCharsets.UTF_8)), status);
    }
}
