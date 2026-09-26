package com.zrlog.admin.web.controller.api;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AdminOriginControllerTest {
    private static final String FRONTEND = "https://admin.example";

    private void configure(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("backend_server_url", "https://api.example/sub");
        db.putWebsite("admin_static_resource_base_url", FRONTEND + "/assets/");
    }

    @Test public void configuredFrontendCanRegisterAuthorizeAndRevokeApplicationsAndTokens() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            configure(db);
            OAuthService service = new OAuthService();
            var client = oauth(FRONTEND, Map.of("name", "Static admin application",
                    "redirectUris", List.of("https://client.example/callback"))).register().getData();
            assertNotNull(client.clientId);

            var token = oauth(FRONTEND, Map.of("name", "Static admin token",
                    "permissionMode", "custom", "permissions", List.of("article.read")))
                    .createPersonalToken().getData();
            assertEquals(1, service.authenticate(token.token, service.adminResource(), Set.of()).userId);
            assertTrue(oauth(FRONTEND, Map.of("id", token.info.id)).revokePersonalToken().getData());
            assertThrows(OAuthException.class, () -> service.authenticate(token.token, service.adminResource(), Set.of()));

            var request = new OAuthModels.AuthorizationRequest();
            request.client_id = client.clientId;
            request.redirect_uri = client.redirectUris.get(0);
            request.response_type = "code";
            request.resource = service.mcpResource();
            request.scope = "articles:read";
            request.code_challenge_method = "S256";
            request.code_challenge = OAuthService.hash(OAuthService.random());
            String requestId = OAuthInterceptor.parameters(URI.create(service.authorize(request)).getRawQuery()).get("request_id");
            var consent = service.consent(requestId);
            var decision = new OAuthModels.Decision();
            decision.requestId = requestId;
            decision.csrf = "wrong";
            decision.approve = true;
            decision.scopes = consent.availableScopes;
            assertEquals("access_denied", assertThrows(OAuthException.class,
                    () -> oauth(FRONTEND, decision).decide()).getOAuthError());
            decision.csrf = consent.csrf;
            String redirect = oauth(FRONTEND, decision).decide().getData().redirectUri;
            assertTrue(redirect.startsWith("https://client.example/callback?code="));
            assertEquals(service.issuer(), OAuthInterceptor.parameters(URI.create(redirect).getRawQuery()).get("iss"));
            String grantId = service.page().grants.get(0).id;
            assertTrue(oauth(FRONTEND, Map.of("id", grantId)).revokeGrant().getData());
            assertTrue(service.page().grants.get(0).revoked);
            assertTrue(oauth(FRONTEND, Map.of("id", client.clientId)).disableClient().getData());
            assertThrows(OAuthException.class, () -> service.authorize(request));
        }
    }

    @Test public void configuredFrontendCanCreateAndUpdateMembers() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            configure(db);
            var member = controller(new MemberController(), FRONTEND, Map.of("userName", "writer",
                    "password", "test-member-password", "role", "author")).create().getData();
            assertEquals("author", member.role);
            var updated = controller(new MemberController(), FRONTEND, Map.of("userId", member.userId,
                    "role", "contributor", "enabled", false)).update().getData();
            assertEquals("contributor", updated.role);
            assertFalse(updated.enabled);
        }
    }

    @Test public void unconfiguredSourcesCannotMutateApplicationsTokensOrMembers() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            configure(db);
            for (String origin : List.of("https://evil.example", "https://admin.example.evil.example")) {
                var oauth = oauth(origin, Map.of());
                var members = controller(new MemberController(), origin, Map.of());
                for (ThrowingRunnable action : List.<ThrowingRunnable>of(oauth::register, oauth::decide,
                        oauth::revokeGrant, oauth::createPersonalToken, oauth::revokePersonalToken,
                        oauth::disableClient, members::create, members::update, members::transfer)) {
                    assertEquals("access_denied", assertThrows(OAuthException.class, action).getOAuthError());
                }
            }
            assertEquals(0L, ((Number) db.scalar("select count(*) from oauth_client")).longValue());
            assertEquals(0L, ((Number) db.scalar("select count(*) from user_access_token")).longValue());
            assertEquals(1L, ((Number) db.scalar("select count(*) from user")).longValue());
        }
    }

    private OAuthAdminController oauth(String origin, Object body) throws Exception {
        return controller(new OAuthAdminController(), origin, body);
    }

    private <T extends Controller> T controller(T controller, String origin, Object body) throws Exception {
        byte[] json = new Gson().toJson(body).getBytes(StandardCharsets.UTF_8);
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMethod": return HttpMethod.POST;
                        case "getInputStream": return new ByteArrayInputStream(json);
                        case "getHeader": return "Origin".equals(args[0]) ? origin : null;
                        case "getHeaderMap": return Map.of("Origin", origin, "X-Real-IP", "127.0.0.1");
                        case "getRemoteHost": return "127.0.0.1";
                        case "getContextPath": return "/sub";
                        case "getUri": return "/api/admin/oauth";
                        default: return null;
                    }
                });
        HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> null);
        Field requestField = Controller.class.getDeclaredField("request");
        requestField.setAccessible(true);
        requestField.set(controller, request);
        Field responseField = Controller.class.getDeclaredField("response");
        responseField.setAccessible(true);
        responseField.set(controller, response);
        return controller;
    }
}
