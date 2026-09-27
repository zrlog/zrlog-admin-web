package com.zrlog.admin.business.service;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import org.junit.Test;

import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/** Exercises interleavings between separate autocommitted statements, without a shared process lock. */
public class D1AccountStorageTest {
    private final OAuthService oauth = new OAuthService(() -> "https://blog.example/sub");
    private final String verifier = OAuthService.random();

    private Client client() throws Exception {
        Client client = new Client(); client.name = "D1 assistant"; client.redirectUris = List.of("http://127.0.0.1:3000/callback");
        return oauth.register(client);
    }

    private String pending(Client client) throws Exception {
        AuthorizationRequest request = new AuthorizationRequest();
        request.client_id = client.clientId; request.redirect_uri = client.redirectUris.get(0);
        request.response_type = "code"; request.resource = oauth.mcpResource(); request.scope = "articles:read offline_access";
        request.code_challenge_method = "S256"; request.code_challenge = OAuthService.hash(verifier);
        return parameters(oauth.authorize(request)).get("request_id");
    }

    private Decision decision(Consent consent) {
        Decision decision = new Decision(); decision.requestId = consent.requestId; decision.csrf = consent.csrf;
        decision.approve = true; decision.scopes = consent.availableScopes;
        return decision;
    }

    private TokenRequest code(Client client, Consent consent) throws Exception {
        TokenRequest request = new TokenRequest(); request.grant_type = "authorization_code";
        request.code = parameters(oauth.decide(decision(consent)).redirectUri).get("code");
        request.client_id = client.clientId; request.redirect_uri = client.redirectUris.get(0);
        request.resource = oauth.mcpResource(); request.code_verifier = verifier;
        return request;
    }

    private Map<String, String> parameters(String uri) { return OAuthInterceptor.parameters(URI.create(uri).getRawQuery()); }

    private static void await(CyclicBarrier barrier) throws SQLException {
        try { barrier.await(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
        catch (BrokenBarrierException | TimeoutException e) { throw new SQLException(e); }
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = pool.submit(first), b = pool.submit(second);
            return Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test public void competingCodeAndRefreshExchangesRevokeTheWinnersGrant() throws Exception {
        for (boolean refresh : List.of(false, true)) {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
                Client client = client(); TokenRequest request = code(client, oauth.consent(pending(client)));
                if (refresh) {
                    TokenResponse previous = oauth.token(request);
                    request.grant_type = "refresh_token"; request.refresh_token = previous.refresh_token;
                }
                CyclicBarrier barrier = new CyclicBarrier(2);
                String hash = OAuthService.hash(refresh ? request.refresh_token : request.code);
                db.beforeWebApiUpdate((sql, args) -> {
                    if (sql.startsWith("update oauth_credential set used=?") && hash.equals(args[1])) await(barrier);
                });
                Callable<TokenResponse> exchange = () -> {
                    try { return oauth.token(request); } catch (OAuthException e) { assertEquals("invalid_grant", e.getOAuthError()); return null; }
                };
                List<TokenResponse> results = race(exchange, exchange);
                assertEquals(1L, results.stream().filter(Objects::nonNull).count());
                TokenResponse issued = results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
                assertThrows(OAuthException.class, () -> oauth.authenticate(issued.access_token, oauth.mcpResource(), Set.of()));
                assertEquals(1, ((Number) db.scalar("select revoked from oauth_grant")).intValue());
            }
        }
    }

    @Test public void competingConsentSessionsCannotReplaceTheWinningAccountBinding() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            Client client = client(); String id = pending(client);
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other", "author");
            CyclicBarrier barrier = new CyclicBarrier(2);
            db.afterWebApiQuery((sql, args) -> {
                if (sql.equals("select * from oauth_credential where hash=? and kind=?") && "pending".equals(args[1])) await(barrier);
            });
            List<Consent> results = race(() -> consentAs(db, id, 1, "admin"), () -> consentAs(db, id, 2, "author"));
            assertEquals(1L, results.stream().filter(Objects::nonNull).count());
            db.afterWebApiQuery((sql, args) -> { });
            int winner = results.get(0) == null ? 2 : 1;
            AccountAuthorizationTest.login(db, winner, winner == 1 ? "admin" : "author");
            Consent consent = results.get(winner - 1);
            TokenResponse token = oauth.token(code(client, consent));
            assertEquals(winner, oauth.authenticate(token.access_token, oauth.mcpResource(), Set.of()).userId);
        }
    }

    private Consent consentAs(InMemoryZrLogDatabase db, String id, int userId, String role) throws Exception {
        try {
            AccountAuthorizationTest.login(db, userId, role);
            try { return oauth.consent(id); } catch (OAuthException e) { assertEquals("invalid_grant", e.getOAuthError()); return null; }
        } finally { AdminTokenThreadLocal.remove(); }
    }

    @Test public void issuanceFailureDoesNotRestoreConsumedCredentials() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            Client client = client(); TokenRequest request = code(client, oauth.consent(pending(client)));
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("insert into oauth_credential") && "refresh".equals(args[1])) throw new SQLException("Simulated interruption");
            });
            assertThrows(SQLException.class, () -> oauth.token(request));
            assertEquals(1, ((Number) db.scalar("select used from oauth_credential where hash=?", OAuthService.hash(request.code))).intValue());
            db.beforeWebApiUpdate((sql, args) -> { });
            assertThrows(OAuthException.class, () -> oauth.token(request));
            assertEquals(1, ((Number) db.scalar("select revoked from oauth_grant")).intValue());
        }
    }

    @Test public void pendingQuotaIsCheckedInTheInsertionStatement() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            Client client = client();
            db.execute("with recursive seq(n) as (select 1 union all select n+1 from seq where n<999) "
                    + "insert into oauth_credential(hash,kind,payload,expiresAt,used) select 'quota-'||n,'pending','{}',?,0 from seq", System.currentTimeMillis() + 600_000);
            CyclicBarrier barrier = new CyclicBarrier(2);
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("insert into oauth_credential") && "pending".equals(args[1])) await(barrier);
            });
            Callable<String> authorize = () -> {
                try { return pending(client); } catch (OAuthException e) { assertEquals(503, e.getStatus()); return null; }
            };
            List<String> results = race(authorize, authorize);
            assertEquals(1L, results.stream().filter(Objects::nonNull).count());
            assertEquals(1000, ((Number) db.scalar("select count(*) from oauth_credential where kind='pending'")).intValue());
        }
    }

    @Test public void memberUpdateAtomicallyChangesPasswordRoleAndCredentialVersion() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AccountAuthorizationTest.login(db, 1, "owner");
            MemberService members = new MemberService();
            MemberModels.Create create = new MemberModels.Create(); create.userName = "writer"; create.role = "author"; create.password = "previous-password";
            int id = members.create(create).userId;
            AccountAuthorizationTest.login(db, id, "author");
            com.zrlog.admin.business.security.PersonalTokenModels.Create pat = new com.zrlog.admin.business.security.PersonalTokenModels.Create(); pat.name = "Assistant";
            String token = new PersonalAccessTokenService(oauth.mcpResource()).create(pat).token;
            AccountAuthorizationTest.login(db, 1, "owner");
            MemberModels.Update update = new MemberModels.Update(); update.userId = id; update.role = "editor"; update.enabled = false; update.password = "replacement-password";
            members.update(update);
            Map<String, Object> row = db.queryOne("select * from user where userId=?", id);
            assertEquals("editor", row.get("role")); assertEquals(0, ((Number) row.get("enabled")).intValue());
            assertEquals(1, ((Number) row.get("authVersion")).intValue());
            assertTrue(PasswordHashUtils.matches(SecurityUtils.md5(update.password), (String) row.get("password")));
            assertFalse(PasswordHashUtils.matches(SecurityUtils.md5(create.password), (String) row.get("password")));
            assertThrows(OAuthException.class, () -> oauth.authenticate(token, oauth.mcpResource(), Set.of()));
        }
    }

    @Test public void memberWritesRecheckActorAndTargetAtTheWrite() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            MemberService members = new MemberService();
            MemberModels.Create create = new MemberModels.Create(); create.userName = "writer"; create.role = "author"; create.password = "previous-password";
            int id = members.create(create).userId;
            MemberModels.Update update = new MemberModels.Update(); update.userId = id; update.role = "contributor"; update.enabled = false;
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update user set role=?")) db.execute("update user set role='admin',authVersion=authVersion+1 where userId=?", id);
            });
            assertThrows(PermissionErrorException.class, () -> members.update(update));
            assertEquals("admin", db.scalar("select role from user where userId=?", id));
            assertEquals(1, ((Number) db.scalar("select enabled from user where userId=?", id)).intValue());
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("insert into user ")) db.execute("update user set role='author',authVersion=authVersion+1 where userId=1");
            });
            create.userName = "too-late";
            assertThrows(PermissionErrorException.class, () -> members.create(create));
            assertEquals(0, ((Number) db.scalar("select count(*) from user where userName='too-late'")).intValue());
        }
    }

    @Test public void competingOwnershipTransfersAlwaysLeaveExactlyOneOwner() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AccountAuthorizationTest.login(db, 1, "owner");
            db.execute("update user set password=? where userId=1", PasswordHashUtils.hash(SecurityUtils.md5("owner-password")));
            db.execute("insert into user(userId,userName,role) values(?,?,?),(?,?,?)", 2, "second", "admin", 3, "third", "admin");
            CyclicBarrier barrier = new CyclicBarrier(2);
            db.beforeWebApiUpdate((sql, args) -> { if (sql.startsWith("with participants")) await(barrier); });
            List<Boolean> results = race(() -> transfer(db, 2), () -> transfer(db, 3));
            assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, ((Number) db.scalar("select count(*) from user where role='owner'")).intValue());
            assertEquals("admin", db.scalar("select role from user where userId=1"));
            assertEquals(2, ((Number) db.scalar("select sum(authVersion) from user")).intValue());
        }
    }

    private boolean transfer(InMemoryZrLogDatabase db, int target) throws Exception {
        try {
            AccountAuthorizationTest.login(db, 1, "owner");
            MemberModels.Transfer transfer = new MemberModels.Transfer(); transfer.userId = target; transfer.password = "owner-password";
            try { new MemberService().transfer(transfer); return true; } catch (PermissionErrorException e) { return false; }
        } finally { AdminTokenThreadLocal.remove(); }
    }
}
