package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.McpWebSetup;

import org.junit.Test;

import java.util.Arrays;

public class OAuthServerAddressTest {

    @Test public void unconfiguredLocalSiteCanLoadApplicationPagesAndDiscovery() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var config = db.config().getServerConfig();
            for (String host : Arrays.asList(null, "", " ")) {
                db.cacheService().getPublicWebSiteInfo().setHost(host);
                for (String context : java.util.List.of("", "/sub")) {
                    config.setContextPath(context);
                    for (int port : new int[]{80, 8080, 18080}) {
                        config.setPort(port);
                        OAuthService service =
                                new OAuthService(
                                        ApplicationServicesFixture.backend(db.config())::configured,
                                        ApplicationServicesFixture.available(
                                                db.config(), McpWebSetup.class),
                                        AccountServicesFixture.component(db.dataSource()).permissions(),
                                        AccountServicesFixture.component(db.dataSource()).security(),
                                        tokenResource ->
                                                new PersonalAccessTokenService(
                                                        tokenResource,
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .permissions(),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .security()),
                                        AccountServicesFixture.component(db.dataSource()).backend());
                        String origin = "http://localhost" + (port == 80 ? "" : ":" + port);
                        String issuer = origin + context;
                        var page = service.page();
                        assertEquals(issuer, page.issuer);
                        assertEquals(issuer + "/api/oauth", page.resource);
                        assertEquals(issuer + "/mcp", page.mcpResource);
                        assertEquals(issuer + WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT, page.notificationEndpoint);
                        assertTrue(page.clients.isEmpty());
                        assertTrue(page.grants.isEmpty());
                        assertTrue(page.personalTokens.isEmpty());
                        assertEquals(issuer + "/oauth/authorize", service.metadata().authorization_endpoint);
                        service.requireAdminOrigin(origin);
                        assertThrows(OAuthException.class, () -> service.requireAdminOrigin("https://evil.example"));
                    }
                }
            }
        }
    }

    @Test public void splitDeploymentUsesBackendForDiscoveryAndDisplayedAddresses() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var accounts = AccountServicesFixture.component(db.dataSource());
            OAuthService service =
                    new OAuthService(
                            () ->
                                    OAuthService.configuredIssuer(
                                            "https://xiaochun-admin.zrlog.com/",
                                            "xiaochun.zrlog.com",
                                            ""),
                            () -> true,
                            accounts.permissions(),
                            accounts.security(),
                            tokenResource ->
                                    new PersonalAccessTokenService(
                                            tokenResource,
                                            accounts.permissions(),
                                            accounts.security()),
                            accounts.backend());
            assertEquals("https://xiaochun-admin.zrlog.com", service.issuer());
            assertEquals("https://xiaochun-admin.zrlog.com/oauth/authorize", service.metadata().authorization_endpoint);
            assertEquals("https://xiaochun-admin.zrlog.com/oauth/token", service.metadata().token_endpoint);
            assertEquals("https://xiaochun-admin.zrlog.com/oauth/revoke", service.metadata().revocation_endpoint);
            assertEquals("https://xiaochun-admin.zrlog.com/mcp", service.mcpResourceMetadata().resource);
            assertEquals(java.util.List.of(service.issuer()), service.mcpResourceMetadata().authorization_servers);
            assertEquals("https://xiaochun-admin.zrlog.com/.well-known/oauth-protected-resource/mcp", service.mcpResourceMetadataUrl());
            assertEquals("https://xiaochun-admin.zrlog.com/api/admin", service.adminResource());
            assertEquals("https://xiaochun-admin.zrlog.com/api/oauth", service.resource());
            var page = service.page();
            assertEquals(service.issuer(), page.issuer);
            assertEquals(service.mcpResource(), page.mcpResource);
            assertEquals(service.issuer() + WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT, page.notificationEndpoint);
            var app = new OAuthModels.Client();
            app.name = "MCP test"; app.redirectUris = java.util.List.of("https://chatgpt.com/connector/oauth/test");
            app = service.register(app);
            var request = new OAuthModels.AuthorizationRequest();
            request.client_id = app.clientId; request.redirect_uri = app.redirectUris.get(0); request.response_type = "code";
            request.code_challenge_method = "S256"; request.code_challenge = OAuthService.hash(OAuthService.random());
            request.scope = "articles:read offline_access";
            for (String wrongResource : java.util.List.of(service.issuer(), "https://xiaochun.zrlog.com/mcp")) {
                request.resource = wrongResource;
                assertEquals("invalid_target", assertThrows(OAuthException.class, () -> service.authorize(request)).getOAuthError());
            }
            request.resource = service.mcpResource();
            assertTrue(service.authorize(request).startsWith(service.issuer() + "/admin/user/applications/authorize?"));
            request.scope += " articles:delete";
            assertEquals("invalid_scope", assertThrows(OAuthException.class, () -> service.authorize(request)).getOAuthError());
        }
    }

    @Test public void backendContextPathIsIncludedExactlyOnce() {
        assertEquals("https://admin.example/sub", OAuthService.configuredIssuer("https://admin.example/", "blog.example", "/sub"));
        assertEquals("https://admin.example/sub", OAuthService.configuredIssuer("https://admin.example/sub/", "blog.example", "/sub"));
        assertEquals("https://admin.example/proxy/sub", OAuthService.configuredIssuer("https://admin.example/proxy/sub", "blog.example", "/sub"));
        assertEquals("http://localhost:18089/sub", OAuthService.configuredIssuer("http://localhost:18089/sub", "blog.example", "/sub"));
        assertEquals("https://blog.example/sub", OAuthService.configuredIssuer(null, "blog.example", "/sub"));
        assertEquals("https://blog.example/sub", OAuthService.configuredIssuer("/", "blog.example", "/sub"));
    }

    @Test public void malformedBackendCannotSilentlyFallBackToBlogHost() {
        for (String invalid : java.util.List.of("admin.example", "/sub", "//admin.example", "http://admin.example",
                "https://user@admin.example", "https://admin.example?site=1", "https://admin.example#fragment")) {
            assertThrows(OAuthException.class, () -> OAuthService.configuredIssuer(invalid, "blog.example", "/sub"));
        }
    }
}
