package com.zrlog.admin.business.service;

import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AccountAuthorizationBoundaryTest {
    private final String database;

    public AccountAuthorizationBoundaryTest(String database) {
        this.database = database;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<String> databases() {
        return List.of("h2", "sqlite");
    }

    private static AdminTokenVO token(int userId, int authVersion) {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(userId);
        token.setAuthVersion(authVersion);
        token.setSessionId("explicit-" + userId);
        return token;
    }

    private static HttpRequest query(String id) {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getParaToStr")) return id;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void explicitIdentityAndAuthorizationDoNotBorrowTheThreadAccount() throws Exception {
        try (var db = open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            var account = AccountServicesFixture.component(db.dataSource());
            var author = account.identities().account(token(2, 0));
            assertEquals(2, author.getUserId());
            assertEquals("author", author.getRole());
            // The fixture's current browser session is an administrator, not this author.
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            assertThrows(PermissionErrorException.class,
                    () -> account.permissions().require(author, AccountAction.FILE_MANAGE));
            AdminTokenThreadLocal.remove();
            var administrator = account.identities().account(token(1, 0));
            account.permissions().require(administrator, AccountAction.FILE_MANAGE);
            account.permissions().administrator(administrator);
            assertNull(AdminTokenThreadLocal.getUser());
            assertThrows(PermissionErrorException.class,
                    () -> account.permissions().require(null, AccountAction.ARTICLE_READ));
        }
    }

    @Test
    public void identityRechecksDisabledDeletedAndRevokedAccounts() throws Exception {
        try (var db = open()) {
            var identities = AccountServicesFixture.component(db.dataSource()).identities();
            AdminTokenVO credentials = token(1, 0);
            assertEquals("admin", identities.account(credentials).getRole());
            db.execute("update user set role='author' where userId=1");
            assertEquals("author", identities.account(credentials).getRole());
            db.execute("update user set authVersion=1 where userId=1");
            assertThrows(PermissionErrorException.class, () -> identities.account(credentials));
            db.execute("update user set enabled=0 where userId=1");
            assertThrows(PermissionErrorException.class, () -> identities.account(token(1, 1)));
            db.execute("delete from user where userId=1");
            assertThrows(PermissionErrorException.class, () -> identities.account(token(1, 1)));
            assertThrows(PermissionErrorException.class, () -> identities.account(null));
        }
    }

    @Test
    public void preferencesUseExplicitActorsAndPublicDefaultsWithoutRequestState() throws Exception {
        try (var db = open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.putWebsite("admin_article_page_size", 20);
            var account = AccountServicesFixture.component(db.dataSource());
            var administrator = account.identities().account(token(1, 0));
            var author = account.identities().account(token(2, 0));
            var preferences = account.preferences();
            // The browser still belongs to user 1; an explicit user 2 must own this write.
            preferences.updateBody(author,
                    "{\"articlePageSize\":15,\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
            assertNull(preferences.current(administrator).overrides.articlePageSize);
            AdminTokenThreadLocal.remove();
            assertEquals(Integer.valueOf(15), preferences.current(author).effective.articlePageSize);
            assertEquals("own_all", preferences.assistant(author).knowledgeScope);
            preferences.update(administrator, JsonParser.parseString("{\"articlePageSize\":40}").getAsJsonObject());
            assertEquals(Integer.valueOf(40), preferences.effective(administrator).articlePageSize);
            assertEquals(Integer.valueOf(15), preferences.effective(author).articlePageSize);
            assertEquals(Integer.valueOf(20), preferences.effective(null).articlePageSize);
            var dashboard = new AdminDashboardConfigResponse();
            dashboard.setAutoRefreshIntervalSeconds(90);
            preferences.saveDashboard(author, dashboard);
            assertEquals(Integer.valueOf(90), preferences.dashboard(author).getAutoRefreshIntervalSeconds());
            assertNull(preferences.dashboard(administrator));
            assertNull(preferences.dashboard(null));
            assertThrows(PermissionErrorException.class, () -> preferences.current(null));
            assertThrows(PermissionErrorException.class, () -> preferences.updateBody(null, "{}"));
            assertThrows(PermissionErrorException.class, () -> preferences.saveDashboard(null, dashboard));
            assertThrows(PermissionErrorException.class, () -> preferences.assistant(null));
            db.execute("update user set enabled=0 where userId=2");
            var disabled = com.zrlog.data.security.AccountAccess.from(
                    db.queryOne("select * from user where userId=2"));
            assertThrows(PermissionErrorException.class, () -> preferences.effective(disabled));
            assertThrows(PermissionErrorException.class, () -> preferences.updateBody(disabled, "{}"));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void memberOperationsAcceptExplicitActorsAndRecheckThemBeforeWriting() throws Exception {
        try (var db = open()) {
            var account = AccountServicesFixture.component(db.dataSource());
            var actor = account.identities().account(token(1, 0));
            AdminTokenThreadLocal.remove();
            MemberModels.Create create = new MemberModels.Create();
            create.userName = "writer";
            create.password = "new-member-password";
            create.role = "author";
            var member = account.members().create(actor, create);
            assertEquals("writer", member.userName);
            assertEquals("author", member.role);
            assertEquals("admin", account.members().list(actor).currentRole);
            MemberModels.Update update = new MemberModels.Update();
            update.userId = member.userId;
            update.role = "contributor";
            update.enabled = true;
            assertEquals("contributor", account.members().update(actor, update).role);
            assertEquals("contributor", db.scalar("select role from user where userId=?", member.userId));
            db.execute("update user set authVersion=1 where userId=1");
            create.userName = "too-late";
            assertThrows(PermissionErrorException.class, () -> account.members().create(actor, create));
            assertEquals(0, ((Number) db.scalar("select count(*) from user where userName='too-late'")).intValue());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void theSameCredentialsResolveAgainstTheirOwnApplicationDatabase() throws Exception {
        try (var first = open(); var second = open()) {
            second.execute("update user set role='author' where userId=1");
            var firstAccount = AccountServicesFixture.component(first.dataSource());
            var secondAccount = AccountServicesFixture.component(second.dataSource());
            AdminTokenThreadLocal.remove();
            assertEquals("admin", firstAccount.identities().account(token(1, 0)).getRole());
            assertEquals("author", secondAccount.identities().account(token(1, 0)).getRole());
            second.execute("update user set authVersion=1 where userId=1");
            assertThrows(PermissionErrorException.class,
                    () -> secondAccount.identities().account(token(1, 0)));
            assertEquals("admin", firstAccount.identities().account(token(1, 0)).getRole());
        }
    }

    @Test
    public void requestDelegationCannotWidenAndIsRestoredAfterDenial() throws Exception {
        try (var db = open()) {
            var account = AccountServicesFixture.component(db.dataSource());
            AdminTokenVO credentials = token(1, 0);
            AdminTokenThreadLocal.withUser(credentials, () -> {
                DelegatedAccess.withPermissions(Set.of(AccountAction.ARTICLE_READ.getId()), false, () -> {
                    var actor = account.requestIdentity().current();
                    account.routeAuthorizer().checkRoute(actor, Routes.class.getMethod("read"), null);
                    assertThrows(PermissionErrorException.class,
                            () -> account.routeAuthorizer().checkRoute(actor, Routes.class.getMethod("manage"), null));
                    assertThrows(PermissionErrorException.class, () -> account.members().list(actor));
                    DelegatedAccess.withPermissions(Set.of(AccountAction.FILE_MANAGE.getId()), true, () -> {
                        assertThrows(PermissionErrorException.class,
                                () -> account.permissions().require(account.requestIdentity().current(), AccountAction.FILE_MANAGE));
                        return null;
                    });
                    assertTrue(AccountAction.ARTICLE_READ.allowed(account.requestIdentity().current()));
                    return null;
                });
                account.permissions().require(account.requestIdentity().current(), AccountAction.FILE_MANAGE);
                return null;
            });
        }
    }

    @Test
    public void routeQueriesUseExplicitActorAndDeclaredOptionalIdPolicy() throws Exception {
        try (var db = open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.execute("insert into log(logId,userId,privacy,rubbish) values(100,2,1,1)");
            var account = AccountServicesFixture.component(db.dataSource());
            var actor = account.identities().account(token(1, 0));
            var author = account.identities().account(token(2, 0));
            AdminTokenThreadLocal.remove();
            var routes = account.routeAuthorizer();
            routes.checkRoute(actor, Routes.class.getMethod("newDraft"), query(""));
            routes.checkRoute(actor, Routes.class.getMethod("detail"), query("0"));
            routes.checkRoute(actor, Routes.class.getMethod("detail"), query("-1"));
            routes.checkRoute(author, Routes.class.getMethod("detail"), query("100"));
            for (String invalid : List.of("", "bad", "-2", "999", "9223372036854775808")) {
                assertThrows(invalid, PermissionErrorException.class,
                        () -> routes.checkRoute(actor, Routes.class.getMethod("detail"), query(invalid)));
            }
            assertThrows(PermissionErrorException.class,
                    () -> routes.checkRoute(author, Routes.class.getMethod("detail"), query("-1")));
            assertThrows(PermissionErrorException.class,
                    () -> routes.checkRoute(actor, Object.class.getMethod("toString"), null));
            assertThrows(PermissionErrorException.class, () -> routes.checkRoute(actor, null, null));
        }
    }

    private InMemoryZrLogDatabase open() throws Exception {
        return "sqlite".equals(database) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }

    public static class Routes {
        @RequiresAction(value = AccountAction.ARTICLE_READ, descriptionKey = "test.read")
        public void read() {
        }

        @RequiresAction(value = AccountAction.FILE_MANAGE, descriptionKey = "test.manage")
        public void manage() {
        }

        @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, descriptionKey = "test.detail")
        public void detail() {
        }

        @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, optionalArticleId = true, descriptionKey = "test.new")
        public void newDraft() {
        }
    }
}
