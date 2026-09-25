package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.knowledge.KnowledgeModels;
import com.zrlog.admin.business.knowledge.KnowledgeService;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.PersonalTokenModels.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PersonalAccessTokenServiceTest {
    @Parameterized.Parameters(name="database={0}")
    public static String[] databases() { return new String[]{"h2", "sqlite", "webapi"}; }
    private final String backend;
    public PersonalAccessTokenServiceTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase database() throws Exception { return "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi() : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open(); }
    private final OAuthService oauth = new OAuthService(() -> "https://blog.example/sub");
    private final PersonalAccessTokenService service = new PersonalAccessTokenService(oauth.mcpResource());
    private Create request(String... scopes) { Create request = new Create(); request.name = "My assistant"; request.scopes = List.of(scopes); return request; }

    @Test public void bindsCreationToTheLoggedInAccountAndReturnsTheSecretOnlyOnce() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Create request = new Gson().fromJson("{\"name\":\"Desktop AI\",\"userId\":999}", Create.class);
            Created created = service.create(request);
            assertEquals(1, created.info.userId);
            assertEquals(List.of("articles:read"), created.info.scopes);
            assertEquals(30 * 86_400_000L, created.info.expiresAt - created.info.createdAt);
            assertEquals(1, oauth.authenticate(created.token, oauth.mcpResource(), Set.of("articles:read")).userId);
            Map<String,Object> stored = db.queryOne("select * from user_access_token where id=?", created.info.id);
            assertEquals(OAuthService.hash(created.token), stored.get("tokenHash"));
            assertFalse(new Gson().toJson(stored).contains(created.token));
            String page = new Gson().toJson(oauth.page());
            assertTrue(page.contains("Desktop AI")); assertFalse(page.contains(created.token)); assertFalse(page.contains(OAuthService.hash(created.token)));
            assertThrows(OAuthException.class, () -> oauth.authenticate(created.token, oauth.resource(), Set.of("articles:read")));
            assertEquals(403, assertThrows(OAuthException.class, () -> oauth.authenticate(created.token, oauth.mcpResource(), Set.of("articles:write"))).getStatus());
        }
    }

    @Test public void onlyListsAndRevokesOwnTokensEvenForAnotherAdministrator() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Created first = service.create(request("articles:read"));
            Created sibling = service.create(request("articles:read"));
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other", "admin");
            AccountAuthorizationTest.login(db, 2, "admin");
            assertTrue(oauth.page().personalTokens.isEmpty());
            service.revoke(first.info.id);
            assertEquals(1, oauth.authenticate(first.token, oauth.mcpResource(), Set.of()).userId);
            Created second = service.create(request("articles:read"));
            assertEquals(2, second.info.userId); assertEquals(1, oauth.page().personalTokens.size());
            AccountAuthorizationTest.login(db, 1, "admin");
            service.revoke(first.info.id); service.revoke(first.info.id); service.revoke("unknown");
            assertThrows(OAuthException.class, () -> oauth.authenticate(first.token, oauth.mcpResource(), Set.of()));
            assertEquals(1, oauth.authenticate(sibling.token, oauth.mcpResource(), Set.of()).userId);
            assertEquals(2, oauth.authenticate(second.token, oauth.mcpResource(), Set.of()).userId);
        }
    }

    @Test public void validatesScopesLifetimeAndNamesBeforeCreatingCredentials() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            for (String scope : List.of("articles:write", "offline_access", "unknown"))
                assertThrows(OAuthException.class, () -> service.create(request("articles:read", scope)));
            assertThrows(OAuthException.class, () -> service.create(request("articles:read_private")));
            for (int days : List.of(-1, 0, 1, 365, Integer.MAX_VALUE)) {
                Create invalid = request("articles:read"); invalid.expiresInDays = days;
                assertThrows(com.zrlog.common.exception.ArgsException.class, () -> service.create(invalid));
            }
            for (String name : Arrays.asList(null, " ", "line\nbreak", "x".repeat(129))) {
                Create invalid = request("articles:read"); invalid.name = name;
                assertThrows(com.zrlog.common.exception.ArgsException.class, () -> service.create(invalid));
            }
            assertEquals(0L, ((Number) db.scalar("select count(*) from user_access_token")).longValue());
            AccountAuthorizationTest.login(db, 1, "author");
            assertFalse(oauth.page().personalTokenScopes.contains("articles:all"));
            assertThrows(OAuthException.class, () -> service.create(request("articles:read", "articles:all")));
            for (int days : List.of(7, 30, 90)) {
                Create valid = request("articles:read"); valid.expiresInDays = days;
                Created token = service.create(valid); assertEquals(days * 86_400_000L, token.info.expiresAt - token.info.createdAt);
            }
        }
    }

    @Test public void expiryRevocationAndAccountChangesInvalidateCredentialsImmediately() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Created expired = service.create(request("articles:read"));
            db.execute("update user_access_token set expiresAt=1 where id=?", expired.info.id);
            assertThrows(OAuthException.class, () -> oauth.authenticate(expired.token, oauth.mcpResource(), Set.of()));
            assertTrue(oauth.page().personalTokens.get(0).expired);
            Created active = service.create(request("articles:read"));
            db.execute("update user set enabled=? where userId=1", false);
            assertThrows(OAuthException.class, () -> oauth.authenticate(active.token, oauth.mcpResource(), Set.of()));
            db.execute("update user set enabled=?,authVersion=authVersion+1 where userId=1", true);
            assertThrows(OAuthException.class, () -> oauth.authenticate(active.token, oauth.mcpResource(), Set.of()));
            AccountAuthorizationTest.login(db, 1, "author");
            assertTrue(oauth.page().personalTokens.stream().allMatch(t -> t.invalidated));
            assertThrows(OAuthException.class, () -> oauth.authenticate("zrmcp_bad", oauth.mcpResource(), Set.of()));
        }
    }

    @Test public void privateDraftAndOtherAuthorsRemainBoundedByTheTokenAndCurrentAccount() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other", "author");
            for (int id = 1; id <= 4; id++) db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)",
                    id, id == 4 ? 2 : 1, 1, "article" + id, "body", id == 3, id == 2);
            assertEquals(List.of(1L), hits(service.create(request("articles:read")).token));
            assertEquals(Set.of(1L, 2L, 3L, 4L), new HashSet<>(hits(service.create(request("articles:read", "articles:read_drafts", "articles:read_private", "articles:all")).token)));
            AccountAuthorizationTest.login(db, 1, "author");
            assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(hits(service.create(request("articles:read", "articles:read_drafts", "articles:read_private")).token)));
        }
    }
    private List<Long> hits(String token) throws Exception {
        var identity = oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read"));
        KnowledgeService knowledge = new KnowledgeService(() -> {
            try { return AccountAccess.load(oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read")).userId); }
            catch (java.sql.SQLException e) { throw new RuntimeException(e); }
        }, new HashSet<>(identity.scopes), () -> "https://blog.example/sub");
        KnowledgeModels.SearchResult result = (KnowledgeModels.SearchResult) knowledge.call("search_articles", JsonParser.parseString("{}").getAsJsonObject());
        return result.articles.stream().map(hit -> hit.id).collect(java.util.stream.Collectors.toList());
    }
}
