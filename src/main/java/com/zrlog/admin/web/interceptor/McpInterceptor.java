package com.zrlog.admin.web.interceptor;

import com.google.gson.*;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.security.*;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.data.security.AccountAccess;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One Streamable HTTP endpoint, JSON responses only; no browser session cookies accepted. */
public class McpInterceptor implements HandleAbleInterceptor {
    @Override public boolean isHandleAble(HttpRequest request) { return "/mcp".equals(request.getUri()); }
    @Override public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        OAuthService oauth = new OAuthService();
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        response.addHeader("Access-Control-Expose-Headers", "WWW-Authenticate");
        try {
            String origin = request.getHeader("Origin");
            if (origin != null) {
                try { oauth.requireSameOrigin(origin); }
                catch (OAuthException e) { response.renderCode(403); return false; }
                response.addHeader("Access-Control-Allow-Origin", origin);
                response.addHeader("Vary", "Origin");
            }
            if (request.getMethod() == HttpMethod.OPTIONS) {
                response.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
                response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, MCP-Protocol-Version");
                response.renderCode(204); return false;
            }
            String header = request.getHeader("Authorization");
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) throw new OAuthException("invalid_token", 401);
            String token = header.substring(7);
            OAuthModels.Identity identity = oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read"));
            if (request.getMethod() != HttpMethod.POST) { response.addHeader("Allow", "POST, OPTIONS"); response.renderCode(405); return false; }
            String version = request.getHeader("MCP-Protocol-Version");
            if (version != null && !McpService.VERSIONS.contains(version)) { response.renderCode(400); return false; }
            String contentType = Objects.toString(request.getHeader("Content-Type"), "").split(";", 2)[0].trim();
            if (!"application/json".equalsIgnoreCase(contentType)) { response.renderCode(415); return false; }
            String accept = Objects.toString(request.getHeader("Accept"), "").toLowerCase(Locale.ROOT);
            if (!accept.contains("application/json") || !accept.contains("text/event-stream")) { response.renderCode(406); return false; }
            ByteBuffer buffer = request.getRequestBodyByteBuffer();
            if (buffer == null || buffer.remaining() > 16384) { response.renderCode(413); return false; }
            KnowledgeService knowledge = new KnowledgeService(() -> {
                try {
                    OAuthModels.Identity current = oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read"));
                    return AccountAccess.load(current.userId);
                } catch (java.sql.SQLException e) { throw new OAuthException("temporarily_unavailable", 503); }
            }, new HashSet<>(identity.scopes));
            McpService.Reply reply = new McpService().handle(StandardCharsets.UTF_8.decode(buffer.asReadOnlyBuffer()).toString(), knowledge);
            if (reply.body == null) response.renderCode(reply.status);
            else json(response, reply.body, reply.status);
        } catch (OAuthException e) {
            if (e.getStatus() == 401 || e.getStatus() == 403) response.addHeader("WWW-Authenticate", "Bearer error=\"" + e.getOAuthError() + "\", resource_metadata=\"" + oauth.mcpResourceMetadataUrl() + "\", scope=\"articles:read\"");
            json(response, new OAuthModels.Error(e.getOAuthError()), e.getStatus());
        } catch (Exception e) { json(response, McpService.error(JsonNull.INSTANCE, -32603, "Internal error", 500).body, 500); }
        return false;
    }
    private static void json(HttpResponse response, Object data, int status) {
        response.setContentType("application/json;charset=UTF-8");
        // JsonObject keeps the required null id on errors without emitting null optional DTO fields.
        String body = data instanceof JsonElement ? data.toString() : new Gson().toJson(data);
        response.write(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), status);
    }
}
