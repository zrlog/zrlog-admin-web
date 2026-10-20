package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.access.AccessComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Exercises the application's generated access graph with explicit identities and sessions.
 */
@RunWith(Parameterized.class)
public class AccessActorBoundaryTest {
    private static final String VERIFIER = "v".repeat(43);
    private final String backend;

    public AccessActorBoundaryTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "database={0}")
    public static String[] databases() {
        return new String[]{"h2", "sqlite", "webapi"};
    }

    static AccountAccess actor(InMemoryZrLogDatabase db, int userId) {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(userId);
        return db.component(AccountComponent.class).identities().account(token);
    }

    static Client client() {
        Client client = new Client();
        client.name = "Explicit access test";
        client.redirectUris = List.of("http://127.0.0.1:3000/callback");
        return client;
    }

    static AuthorizationRequest request(OAuthService oauth, Client client, String scopes) {
        var request = new AuthorizationRequest();
        request.client_id = client.clientId;
        request.redirect_uri = client.redirectUris.get(0);
        request.response_type = "code";
        request.scope = scopes;
        request.resource = oauth.adminResource();
        request.code_challenge_method = "S256";
        request.code_challenge = OAuthService.hash(VERIFIER);
        return request;
    }

    static Decision decision(Consent consent) {
        var decision = new Decision();
        decision.requestId = consent.requestId;
        decision.csrf = consent.csrf;
        decision.scopes = List.of("article.read");
        decision.approve = true;
        return decision;
    }

    private static TokenResponse exchange(OAuthService oauth, AuthorizationRequest request, Redirect redirect) throws Exception {
        var exchange = new TokenRequest();
        exchange.grant_type = "authorization_code";
        exchange.client_id = request.client_id;
        exchange.redirect_uri = request.redirect_uri;
        exchange.resource = request.resource;
        exchange.code_verifier = VERIFIER;
        exchange.code = parameters(redirect.redirectUri).get("code");
        return oauth.token(exchange);
    }

    static Map<String, String> parameters(String uri) {
        return OAuthInterceptor.parameters(URI.create(uri).getRawQuery());
    }

    @Test
    public void explicitAccountOwnsClientsConsentsAndGrantsWithoutThreadIdentity() throws Exception {
        try (var db = database()) {
            var oauth = db.component(AccessComponent.class).oauth();
            var actor = actor(db, 2);
            var administrator = actor(db, 1);
            var authority = new GrantAuthority(actor, true);
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            Client client = oauth.register(actor, client());
            AdminTokenThreadLocal.remove();
            var request = request(oauth, client, "article.read");
            String id = parameters(oauth.authorize(request)).get("request_id");
            Consent consent = oauth.consent(authority, "explicit-session", id);
            TokenResponse token = exchange(oauth, request, oauth.decide(authority, "explicit-session", decision(consent)));
            assertEquals(2, oauth.authenticate(token.access_token, oauth.adminResource(), Set.of()).userId);
            assertTrue(oauth.page(administrator).grants.isEmpty());
            String grant = oauth.page(actor).grants.get(0).id;
            oauth.revokeGrant(administrator, grant);
            assertEquals(2, oauth.authenticate(token.access_token, oauth.adminResource(), Set.of()).userId);
            oauth.revokeGrant(actor, grant);
            assertThrows(OAuthException.class, () -> oauth.authenticate(token.access_token, oauth.adminResource(), Set.of()));
            oauth.disableClient(actor, client.clientId);
            assertTrue(oauth.page(actor).clients.isEmpty());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void delegatedActionsControlClientAndGrantManagementIndependentlyOfRole() throws Exception {
        try (var db = database()) {
            var oauth = db.component(AccessComponent.class).oauth();
            var actor = actor(db, 2);
            var grantOnly = actor.restrictActions(Set.of("oauth.grant.manage", "article.read"));
            var clientOnly = actor.restrictActions(Set.of("oauth.client.manage"));
            Client client = oauth.register(clientOnly, client());
            var request = request(oauth, client, "article.read");
            String id = parameters(oauth.authorize(request)).get("request_id");
            var authority = new GrantAuthority(grantOnly, false);
            Consent consent = oauth.consent(authority, "session", id);
            oauth.decide(authority, "session", decision(consent));
            assertTrue(oauth.page(clientOnly).administrator);
            assertEquals(1, oauth.page(clientOnly).clients.size());
            assertTrue(oauth.page(clientOnly).grants.isEmpty());
            assertTrue(oauth.page(clientOnly).personalTokens.isEmpty());
            assertFalse(oauth.page(grantOnly).administrator);
            assertTrue(oauth.page(grantOnly).clients.isEmpty());
            assertEquals(1, oauth.page(grantOnly).grants.size());
            assertThrows(PermissionErrorException.class, () -> oauth.register(grantOnly, client()));
            assertThrows(PermissionErrorException.class, () -> oauth.disableClient(grantOnly, client.clientId));
            assertThrows(PermissionErrorException.class, () -> oauth.revokeGrant(clientOnly, "unknown"));
            assertThrows(PermissionErrorException.class, () -> oauth.consent(new GrantAuthority(clientOnly, false), "session", "unknown"));
            var noManagement = actor.restrictActions(Set.of("article.read"));
            assertThrows(PermissionErrorException.class, () -> oauth.page(noManagement));
            assertThrows(PermissionErrorException.class, () -> oauth.register(actor(db, 3), client()));
        }
    }

    @Test
    public void explicitGrantCeilingIsStableAcrossAmbientDelegationChanges() throws Exception {
        try (var db = database()) {
            var oauth = db.component(AccessComponent.class).oauth();
            var actor = actor(db, 2);
            Client client = oauth.register(actor, client());
            var request = request(oauth, client, "account:inherit");
            String id = parameters(oauth.authorize(request)).get("request_id");
            var limited = new GrantAuthority(actor.restrictActions(Set.of("oauth.grant.manage", "article.read")), false);
            Consent consent = oauth.consent(limited, "session", id);
            assertEquals(Set.of("article.read", "oauth.grant.manage"), Set.copyOf(consent.availableScopes));
            Decision decision = decision(consent);
            decision.scopes = List.of("account:inherit");
            assertThrows(OAuthException.class, () -> oauth.decide(limited, "session", decision));
            decision.scopes = List.of("site.configure");
            assertThrows(OAuthException.class, () -> oauth.decide(limited, "session", decision));
            decision.scopes = List.of("article.read");
            TokenResponse token = exchange(oauth, request, oauth.decide(limited, "session", decision));
            assertEquals(List.of("article.read"), oauth.authenticate(token.access_token, oauth.adminResource(), Set.of()).permissions);
            String inheritedId = parameters(oauth.authorize(request)).get("request_id");
            AdminTokenThreadLocal.remove();
            var full = new GrantAuthority(actor, true);
            Redirect redirect = DelegatedAccess.withPermissions(Set.of("article.read"), false, () -> {
                Consent inherited = oauth.consent(full, "session", inheritedId);
                assertTrue(inherited.availableScopes.contains("account:inherit"));
                Decision approval = decision(inherited);
                approval.scopes = List.of("account:inherit");
                return oauth.decide(full, "session", approval);
            });
            TokenResponse inherited = exchange(oauth, request, redirect);
            assertTrue(oauth.authenticate(inherited.access_token, oauth.adminResource(), Set.of()).permissions.contains("site.configure"));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void explicitSessionAndCsrfRemainBoundAndApprovalIsConsumedOnce() throws Exception {
        try (var db = database()) {
            var oauth = db.component(AccessComponent.class).oauth();
            var actor = actor(db, 2);
            var authority = new GrantAuthority(actor, true);
            Client client = oauth.register(actor, client());
            var request = request(oauth, client, "article.read");
            String id = parameters(oauth.authorize(request)).get("request_id");
            AdminTokenThreadLocal.remove();
            for (String missing : new String[]{null, "", " "}) {
                assertThrows(OAuthException.class, () -> oauth.consent(authority, missing, id));
            }
            Consent consent = oauth.consent(authority, "original-session", id);
            assertThrows(OAuthException.class, () -> oauth.consent(authority, "another-session", id));
            Decision decision = decision(consent);
            assertThrows(OAuthException.class, () -> oauth.decide(authority, "another-session", decision));
            assertThrows(OAuthException.class, () -> oauth.decide(authority, null, decision));
            decision.csrf = "wrong";
            assertThrows(OAuthException.class, () -> oauth.decide(authority, "original-session", decision));
            decision.csrf = consent.csrf;
            TokenResponse token = exchange(oauth, request, oauth.decide(authority, "original-session", decision));
            assertEquals(2, oauth.authenticate(token.access_token, oauth.adminResource(), Set.of()).userId);
            assertThrows(OAuthException.class, () -> oauth.decide(authority, "original-session", decision));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void disabledStaleAndChangedRoleActorsCannotManageOrApproveCredentials() throws Exception {
        for (String revocation : List.of("enabled=false", "role='author'", "authVersion=1")) {
            try (var db = database()) {
                var oauth = db.component(AccessComponent.class).oauth();
                var actor = actor(db, 2);
                var authority = new GrantAuthority(actor, true);
                Client client = oauth.register(actor, client());
                var request = request(oauth, client, "article.read");
                String id = parameters(oauth.authorize(request)).get("request_id");
                Consent consent = oauth.consent(authority, "session", id);
                db.execute("update user set " + revocation + " where userId=2");
                assertThrows(PermissionErrorException.class, () -> oauth.register(actor, client()));
                assertThrows(PermissionErrorException.class, () -> oauth.page(actor));
                assertThrows(PermissionErrorException.class, () -> oauth.revokeGrant(actor, "unknown"));
                assertThrows(PermissionErrorException.class, () -> oauth.disableClient(actor, client.clientId));
                assertThrows(PermissionErrorException.class, () -> oauth.consent(authority, "session", id));
                assertThrows(PermissionErrorException.class, () -> oauth.decide(authority, "session", decision(consent)));
                assertEquals(1, ((Number) db.scalar("select count(*) from oauth_client")).intValue());
                assertEquals(0, ((Number) db.scalar("select count(*) from oauth_grant")).intValue());
            }
        }
    }

    private InMemoryZrLogDatabase database() throws Exception {
        var db = "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi()
                : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        try {
            db.execute("insert into user(userId,userName,role) values(2,'operator','admin'),(3,'writer','author')");
            db.loadAdminWebModules();
            return db;
        } catch (Exception | java.lang.Error error) {
            db.close();
            throw error;
        }
    }
}
