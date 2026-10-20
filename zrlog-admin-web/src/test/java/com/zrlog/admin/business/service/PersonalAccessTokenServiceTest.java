package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.KnowledgeModels;
import com.zrlog.admin.business.knowledge.KnowledgeService;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.business.security.PersonalTokenModels.Create;
import com.zrlog.admin.business.security.PersonalTokenModels.Created;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;
import com.zrlog.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PersonalAccessTokenServiceTest {
    private final String backend;
    private AccountComponent accounts;
    private OAuthService oauth;
    private PersonalAccessTokenService service;

    public PersonalAccessTokenServiceTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "database={0}")
    public static String[] databases() {
        return new String[]{"h2", "sqlite", "webapi"};
    }

    private AccountAccess actor() {
        return accounts.requestIdentity().current();
    }

    private GrantAuthority authority() {
        return new GrantAuthority(actor(), !com.zrlog.admin.business.security.DelegatedAccess.restricted());
    }

    private InMemoryZrLogDatabase database() throws Exception {
        InMemoryZrLogDatabase db = "webapi".equals(backend)
                ? InMemoryZrLogDatabase.openWebApi()
                : "sqlite".equals(backend)
                ? InMemoryZrLogDatabase.openSqlite()
                : InMemoryZrLogDatabase.open();
        try {
            accounts = AccountServicesFixture.component(db.dataSource());
            oauth = new OAuthService(
                    () -> "https://blog.example/sub",
                    () -> true,
                    accounts.permissions(), accounts.identities(),
                    accounts.security(),
                    resource -> new PersonalAccessTokenService(
                            resource, accounts.identities(), accounts.permissions(), accounts.security()),
                    accounts.backend());
            service = new PersonalAccessTokenService(
                    oauth.mcpResource(), accounts.identities(), accounts.permissions(), accounts.security());
            return db;
        } catch (Exception | Error e) {
            db.close();
            throw e;
        }
    }

    private Create request(String... scopes) {
        Create request = new Create();
        request.name = "My assistant";
        request.scopes = List.of(scopes);
        return request;
    }

    @Test
    public void explicitActorOwnsIssuanceListingAndRevocationWithoutThreadIdentity() throws Exception {
        try (var db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            var credentials = new com.zrlog.common.vo.AdminTokenVO();
            credentials.setUserId(2);
            var author = accounts.identities().account(credentials);
            var administrator = actor();
            var authority = new GrantAuthority(author, true);
            Created first = service.create(authority, general("custom", "article.read"));
            assertEquals(2, first.info.userId);
            AdminTokenThreadLocal.remove();
            Created second = service.create(authority, general("inherit"));
            assertEquals(2, oauth.authenticate(second.token, oauth.adminResource(), Set.of()).userId);
            assertEquals(2, service.list(author).size());
            assertTrue(service.list(administrator).isEmpty());
            service.revoke(administrator, first.info.id);
            assertEquals(2, oauth.authenticate(first.token, oauth.adminResource(), Set.of()).userId);
            service.revoke(author, first.info.id);
            assertThrows(OAuthException.class, () -> oauth.authenticate(first.token, oauth.adminResource(), Set.of()));
            assertEquals(2, oauth.authenticate(second.token, oauth.adminResource(), Set.of()).userId);
            var noGrantPermission = author.restrictActions(Set.of("article.read"));
            assertThrows(PermissionErrorException.class, () -> service.list(noGrantPermission));
            assertThrows(PermissionErrorException.class, () -> service.revoke(noGrantPermission, second.info.id));
            assertThrows(PermissionErrorException.class, () -> service.create(new GrantAuthority(noGrantPermission, true), general("inherit")));
            db.execute("update user set authVersion=authVersion+1 where userId=2");
            assertThrows(PermissionErrorException.class, () -> service.create(authority, general("inherit")));
            assertThrows(PermissionErrorException.class, () -> service.list(author));
            assertThrows(PermissionErrorException.class, () -> service.revoke(author, second.info.id));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void explicitDelegationCeilingCannotBorrowAmbientPermissionOrInheritance() throws Exception {
        try (var db = database()) {
            var full = actor();
            var limited = new GrantAuthority(full.restrictActions(Set.of("article.read", "oauth.grant.manage")), false);
            assertFalse(DelegatedAccess.restricted());
            assertThrows(OAuthException.class, () -> service.create(limited, general("inherit")));
            assertThrows(OAuthException.class, () -> service.create(limited, general("custom", "site.configure")));
            Created token = service.create(limited, general("custom", "article.read"));
            assertEquals(List.of("article.read"), oauth.authenticate(token.token, oauth.adminResource(), Set.of()).permissions);
            AdminTokenThreadLocal.remove();
            Created inherited = DelegatedAccess.withPermissions(Set.of("article.read"), false,
                    () -> service.create(new GrantAuthority(full, true), general("inherit")));
            assertTrue(oauth.authenticate(inherited.token, oauth.adminResource(), Set.of()).permissions.contains("site.configure"));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void bindsCreationToTheLoggedInAccountAndReturnsTheSecretOnlyOnce() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Create request = new Gson().fromJson("{\"name\":\"Desktop AI\",\"userId\":999}", Create.class);
            Created created = service.create(authority(), request);
            assertEquals(1, created.info.userId);
            assertEquals(List.of("articles:read"), created.info.scopes);
            assertEquals(30 * 86_400_000L, created.info.expiresAt - created.info.createdAt);
            assertEquals(1, oauth.authenticate(created.token, oauth.mcpResource(), Set.of("articles:read")).userId);
            Map<String, Object> stored = db.queryOne("select * from user_access_token where id=?", created.info.id);
            assertEquals(OAuthService.hash(created.token), stored.get("tokenHash"));
            assertFalse(new Gson().toJson(stored).contains(created.token));
            String page = new Gson().toJson(oauth.page(actor()));
            assertTrue(page.contains("Desktop AI"));
            assertFalse(page.contains(created.token));
            assertFalse(page.contains(OAuthService.hash(created.token)));
            assertThrows(OAuthException.class, () -> oauth.authenticate(created.token, oauth.resource(), Set.of("articles:read")));
            assertEquals(403, assertThrows(OAuthException.class, () -> oauth.authenticate(created.token, oauth.mcpResource(), Set.of("articles:write"))).getStatus());
        }
    }

    @Test
    public void onlyListsAndRevokesOwnTokensEvenForAnotherAdministrator() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Created first = service.create(authority(), request("articles:read"));
            Created sibling = service.create(authority(), request("articles:read"));
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other", "admin");
            AccountAuthorizationTest.login(db, 2, "admin");
            assertTrue(oauth.page(actor()).personalTokens.isEmpty());
            service.revoke(actor(), first.info.id);
            assertEquals(1, oauth.authenticate(first.token, oauth.mcpResource(), Set.of()).userId);
            Created second = service.create(authority(), request("articles:read"));
            assertEquals(2, second.info.userId);
            assertEquals(1, oauth.page(actor()).personalTokens.size());
            AccountAuthorizationTest.login(db, 1, "admin");
            service.revoke(actor(), first.info.id);
            service.revoke(actor(), first.info.id);
            service.revoke(actor(), "unknown");
            assertThrows(OAuthException.class, () -> oauth.authenticate(first.token, oauth.mcpResource(), Set.of()));
            assertEquals(1, oauth.authenticate(sibling.token, oauth.mcpResource(), Set.of()).userId);
            assertEquals(2, oauth.authenticate(second.token, oauth.mcpResource(), Set.of()).userId);
        }
    }

    @Test
    public void validatesScopesLifetimeAndNamesBeforeCreatingCredentials() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            for (String scope : List.of("articles:write", "offline_access", "unknown"))
                assertThrows(OAuthException.class, () -> service.create(authority(), request("articles:read", scope)));
            assertThrows(OAuthException.class, () -> service.create(authority(), request("articles:read_private")));
            for (int days : List.of(-1, 0, 1, 365, Integer.MAX_VALUE)) {
                Create invalid = request("articles:read");
                invalid.expiresInDays = days;
                assertThrows(ArgsException.class, () -> service.create(authority(), invalid));
            }
            for (String name : Arrays.asList(null, " ", "line\nbreak", "x".repeat(129))) {
                Create invalid = request("articles:read");
                invalid.name = name;
                assertThrows(ArgsException.class, () -> service.create(authority(), invalid));
            }
            assertEquals(0L, ((Number) db.scalar("select count(*) from user_access_token")).longValue());
            AccountAuthorizationTest.login(db, 1, "author");
            assertFalse(oauth.page(actor()).personalTokenScopes.contains("articles:all"));
            assertThrows(OAuthException.class, () -> service.create(authority(), request("articles:read", "articles:all")));
            for (int days : List.of(7, 30, 90)) {
                Create valid = request("articles:read");
                valid.expiresInDays = days;
                Created token = service.create(authority(), valid);
                assertEquals(days * 86_400_000L, token.info.expiresAt - token.info.createdAt);
            }
        }
    }

    @Test
    public void expiryRevocationAndAccountChangesInvalidateCredentialsImmediately() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Created expired = service.create(authority(), request("articles:read"));
            db.execute("update user_access_token set expiresAt=1 where id=?", expired.info.id);
            assertThrows(OAuthException.class, () -> oauth.authenticate(expired.token, oauth.mcpResource(), Set.of()));
            assertTrue(oauth.page(actor()).personalTokens.get(0).expired);
            Created active = service.create(authority(), request("articles:read"));
            db.execute("update user set enabled=? where userId=1", false);
            assertThrows(OAuthException.class, () -> oauth.authenticate(active.token, oauth.mcpResource(), Set.of()));
            db.execute("update user set enabled=?,authVersion=authVersion+1 where userId=1", true);
            assertThrows(OAuthException.class, () -> oauth.authenticate(active.token, oauth.mcpResource(), Set.of()));
            AccountAuthorizationTest.login(db, 1, "author");
            assertTrue(oauth.page(actor()).personalTokens.stream().allMatch(t -> t.invalidated));
            assertThrows(OAuthException.class, () -> oauth.authenticate("zrmcp_bad", oauth.mcpResource(), Set.of()));
        }
    }

    @Test
    public void privateDraftAndOtherAuthorsRemainBoundedByTheTokenAndCurrentAccount() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "other", "author");
            for (int id = 1; id <= 4; id++)
                db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)",
                        id, id == 4 ? 2 : 1, 1, "article" + id, "body", id == 3, id == 2);
            assertEquals(List.of(1L), hits(service.create(authority(), request("articles:read")).token, db.dataSource()));
            assertEquals(Set.of(1L, 2L, 3L, 4L), new HashSet<>(hits(service.create(authority(), request("articles:read", "articles:read_drafts", "articles:read_private", "articles:all")).token, db.dataSource())));
            AccountAuthorizationTest.login(db, 1, "author");
            assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(hits(service.create(authority(), request("articles:read", "articles:read_drafts", "articles:read_private")).token, db.dataSource())));
        }
    }

    private Create general(String mode, String... permissions) {
        Create request = new Create();
        request.name = "Java client";
        request.permissionMode = mode;
        request.permissions = List.of(permissions);
        return request;
    }

    @Test
    public void customAndInheritedTokensUseCurrentAccountActionsWithoutExpandingLegacyTokens() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            Created limited = service.create(authority(), general("custom", "article.read", "taxonomy.read"));
            assertTrue(limited.token.startsWith("zrpat_"));
            assertEquals(oauth.issuer(), limited.info.resource);
            var identity = oauth.authenticate(limited.token, oauth.adminResource(), Set.of());
            assertEquals(Set.of("article.read", "taxonomy.read"), new HashSet<>(identity.permissions));
            assertFalse(identity.scopes.contains("articles:write"));
            assertEquals(Set.of("articles:read", "articles:read_drafts", "articles:read_private", "articles:all"), new HashSet<>(identity.scopes));
            assertEquals(identity.scopes, oauth.authenticate(limited.token, oauth.mcpResource(), Set.of("articles:read")).scopes);
            Created inherited = service.create(authority(), general("inherit"));
            assertEquals(
                    PersonalAccessTokenService.availablePermissions(
                            AccountServicesFixture.component(db.dataSource()).requestIdentity().current()),
                    oauth.authenticate(inherited.token, oauth.adminResource(), Set.of())
                            .permissions);
            Created legacy = service.create(authority(), request("articles:read"));
            assertEquals(401, assertThrows(OAuthException.class, () -> oauth.authenticate(legacy.token, oauth.adminResource(), Set.of())).getStatus());
            assertThrows(OAuthException.class, () -> service.create(authority(), general("custom", "unknown.action")));
            AccountAuthorizationTest.login(db, 1, "contributor");
            assertFalse(oauth.authenticate(inherited.token, oauth.adminResource(), Set.of()).permissions.contains("article.publish"));
            assertThrows(OAuthException.class, () -> service.create(authority(), general("custom", "site.configure")));
            db.execute("update user set authVersion=authVersion+1 where userId=1");
            assertThrows(OAuthException.class, () -> oauth.authenticate(inherited.token, oauth.adminResource(), Set.of()));
        }
    }

    @Test
    public void delegatedCredentialsCannotMintBroaderPermissionsAndConditionalChecksRemainEffective() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            AccountAuthorizationTest.login(db, 1, "owner");
            Created token = service.create(authority(), general("custom", "article.create", "oauth.grant.manage", "member.manage"));
            var identity = oauth.authenticate(token.token, oauth.adminResource(), Set.of());
            DelegatedAccess.withPermissions(
                    java.util.Set.copyOf(identity.permissions),
                    "inherit".equals(identity.permissionMode),
                    () -> {
                        assertFalse(
                                AccountServicesFixture.component(db.dataSource())
                                        .requestIdentity()
                                        .current()
                                        .canPublish());
                        assertThrows(
                                OAuthException.class, () -> service.create(authority(), general("inherit")));
                        assertThrows(
                                OAuthException.class,
                                () -> service.create(authority(), general("custom", "site.configure")));
                        assertEquals(
                                List.of("article.create"),
                                service.create(authority(), general("custom", "article.create"))
                                        .info
                                        .permissions);
                        var create = new CreateArticleRequest();
                        create.setTitle("No publication");
                        create.setContent("body");
                        create.setTypeId(1L);
                        create.setRubbish(false);
                        assertThrows(
                                PermissionErrorException.class,
                                () ->
                                        new AdminArticleService(ApplicationServicesFixture.capability(
                                                db.config(),
                                                ArticleAssistant.class),
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .permissions(),
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .identities(),
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .preferences(),
                                                new ArticleVersionService(AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .permissions(),
                                                        () -> new Log(db.dataSource()),
                                                        () ->
                                                                new LogVersion(
                                                                        db.dataSource())),
                                                new ArticlePinningService(
                                                        () -> new Log(db.dataSource()),
                                                        ApplicationServicesFixture.locks(
                                                                db.dataSource())),
                                                () -> new Log(db.dataSource()),
                                                ApplicationServicesFixture.locks(
                                                        db.dataSource()))
                                                .create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), create));
                        var member = new MemberModels.Create();
                        member.userName = "limited";
                        member.password = "long-password-123";
                        member.role = "admin";
                        assertThrows(
                                PermissionErrorException.class,
                                () ->
                                        new MemberService(
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .security(),
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .users(),
                                                AccountServicesFixture.component(
                                                                db.dataSource())
                                                        .permissions())
                                                .create(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), member));
                        return null;
                    });
            assertTrue(AccountServicesFixture.component(db.dataSource()).requestIdentity().current().canPublish());
        }
    }

    private List<Long> hits(String token, DataSourceWrapper testDataSource) throws Exception {
        var identity = oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read"));
        KnowledgeService knowledge =
                new KnowledgeService(
                        () -> {
                            try {
                                return AccountAccess.from(new User(testDataSource).loadById(oauth.authenticate(token, oauth.mcpResource(), Set.of("articles:read")).userId));
                            } catch (java.sql.SQLException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        new HashSet<>(identity.scopes),
                        () -> "https://blog.example/sub",
                        () -> false,
                        () -> new Log(testDataSource));
        KnowledgeModels.SearchResult result = (KnowledgeModels.SearchResult) knowledge.call("search_articles", JsonParser.parseString("{}").getAsJsonObject());
        return result.articles.stream().map(hit -> hit.id).collect(Collectors.toList());
    }
}
