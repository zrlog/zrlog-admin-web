package com.zrlog.admin.business.service;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.controller.ai.AICommentController;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.interceptor.AdminInterceptor;
import com.zrlog.admin.web.interceptor.AdminInternalAiInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.security.ArticleAccess;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AccountAuthorizationTest {
    static void login(InMemoryZrLogDatabase db, int id, String role) throws Exception {
        db.execute("update user set role=? where userId=?", role, id);
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(id);
        token.setSessionId("session-" + id);
        token.setAuthVersion(((Number) db.scalar("select authVersion from user where userId=?", id)).intValue());
        java.lang.reflect.Method setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true);
        AdminTokenThreadLocal.remove();
        setter.invoke(null, token);
    }

    static AccountAccess account(String role) {
        return AccountAccess.from(Map.of("userId", 1, "role", role, "enabled", true, "authVersion", 0));
    }

    @Test
    public void privateAndDraftScopesMustIntersectCurrentRoleAndOwnership() {
        for (String role : AccountAccess.ROLES) {
            AccountAccess a = account(role);
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read"), 1, false, true));
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read", "articles:read_private"), 1, true, true));
            assertTrue(ArticleAccess.canRead(a, a.scopes(), 1, true, true));
            assertEquals(a.isAdministrator(), ArticleAccess.canRead(a, a.scopes(), 2, true, true));
            assertEquals(a.managesAllArticles(), ArticleAccess.canRead(a, a.scopes(), 2, true, false));
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read"), 2, false, false));
            assertFalse(ArticleAccess.canWrite(a, Set.of("articles:write"), 1, false, false, false, false));
            assertEquals(a.canPublish(), ArticleAccess.canWrite(a, a.scopes(), 1, true, false, false, false));
        }
        assertFalse(AccountAction.MEMBER_MANAGE.allowed(account("editor")));
        assertFalse(AccountAction.ADMIN_APPOINT.allowed(account("admin")));
        assertFalse(AccountAction.ARTICLE_DELETE.allowed(account("contributor")));
        assertFalse(ArticleAccess.canRead(AccountAccess.from(null), Set.of("articles:read"), 1, false, false));
    }

    @Test
    public void everyRegisteredAdminRouteRequiresAnExplicitAuthenticationPolicy() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            ServerConfig config = db.config().getServerConfig();
            assertFalse(config.getRouter().getRouterMap().containsKey("/api/admin/knowledge/chat"));
            assertNotNull(config.getRouter().getMethod("/api/admin/article/ai", HttpMethod.POST));
            java.lang.reflect.Method commentAnalyze = AICommentController.class.getMethod("analyze");
            assertEquals(
                    commentAnalyze,
                    config.getRouter()
                            .getMethod(
                                    AdminInternalAiInterceptor.COMMENT_ANALYZE_PATH,
                                    HttpMethod.POST));
            int internalAiIndex = config.getInterceptors().indexOf(AdminInternalAiInterceptor.class);
            assertTrue("Internal AI route must have plugin-token authentication", internalAiIndex >= 0);
            assertTrue("Plugin-token authentication must precede account authentication",
                    internalAiIndex < config.getInterceptors().indexOf(AdminInterceptor.class));
            Set<String> ids = new HashSet<>();
            for (AccountAction action : AccountAction.values()) assertTrue(ids.add(action.getId()));
            config.getRouter().getRouterMap().forEach((path, method) -> {
                // This exact route authenticates plugin-core, without an admin account session.
                if (AdminInternalAiInterceptor.COMMENT_ANALYZE_PATH.equals(path)) {
                    assertEquals(commentAnalyze, method);
                    return;
                }
                if (path.contains("admin")) {
                    RequiresAction binding = method.getAnnotation(RequiresAction.class);
                    assertNotNull(path + " -> " + method, binding);
                    assertFalse(path + " missing description", binding.descriptionKey().isBlank());
                }
            });
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            db.component(AccountComponent.class).routeAuthorizer().checkRoute(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), Object.class.getMethod("toString"), null));
        }
    }

    @Test
    public void articleServiceMustPreserveAuthorAndRejectForeignPrivateAndPublishedWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "writer", "author");
            login(db, 1, "owner");
            AdminArticleService service =
                    db.component(ContentComponent.class).articles();
            CreateArticleRequest create = new CreateArticleRequest();
            create.setTitle("Private");
            create.setTypeId(1L);
            create.setPrivacy(true);
            create.setRubbish(true);
            create.setContent("secret");
            long id = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), create).getLogId();
            login(db, 2, "editor");
            assertThrows(
                    PermissionErrorException.class,
                    () -> db.component(AccountComponent.class).permissions().readArticle(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), id));
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            new ArticleVersionService(db.component(AccountComponent.class).permissions(),
                                    () -> new Log(db.dataSource()),
                                    () -> new LogVersion(db.dataSource()))
                                    .listVersions(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), (int) id));
            db.execute("update log set privacy=? where logId=?", false, id);
            UpdateArticleRequest update = new UpdateArticleRequest();
            update.setLogId((int) id);
            update.setTitle("Edited");
            update.setTypeId(1L);
            update.setVersion(0);
            update.setRubbish(true);
            update.setContent("edited");
            service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), update);
            assertEquals(1, ((Number) db.scalar("select userId from log where logId=?", id)).intValue());
            login(db, 2, "author");
            assertThrows(PermissionErrorException.class, () -> service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), update));
            assertThrows(PermissionErrorException.class, () -> service.delete(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), id));
            login(db, 2, "contributor");
            create.setPrivacy(false);
            create.setRubbish(false);
            assertThrows(PermissionErrorException.class, () -> service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), create));
            create.setRubbish(true);
            long own = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), create).getLogId();
            assertThrows(PermissionErrorException.class, () -> service.delete(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), own));
            db.execute("update user set authVersion=authVersion+1 where userId=2");
            assertThrows(
                    PermissionErrorException.class,
                    db.component(AccountComponent.class).requestIdentity()::current);
        }
    }

    @Test
    public void membersCannotEscalateOrModifyOwnerAndTransferRevokesBothSessions() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try (InMemoryZrLogDatabase db = "webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi() : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open()) {
                login(db, 1, "owner");
                MemberService service =
                        db.component(AccountComponent.class).members();
                MemberModels.Create create = new MemberModels.Create();
                create.userName = "second";
                create.password = "a-long-new-password";
                create.role = "admin";
                int admin = service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), create).userId;
                assertFalse(String.valueOf(db.scalar("select password from user where userId=?", admin)).contains(create.password));
                login(db, admin, "admin");
                assertThrows(PermissionErrorException.class, () -> service.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), create));
                MemberModels.Update update = new MemberModels.Update();
                update.userId = 1;
                update.role = "author";
                update.enabled = false;
                assertThrows(PermissionErrorException.class, () -> service.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), update));
                login(db, 1, "owner");
                db.execute(
                        "update user set password=? where userId=1",
                        PasswordHashUtils.hash(SecurityUtils.md5("owner-password")));
                MemberModels.Transfer transfer = new MemberModels.Transfer();
                transfer.userId = admin;
                transfer.password = "owner-password";
                service.transfer(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), transfer);
                assertEquals(1, ((Number) db.scalar("select count(*) from user where role='owner'")).intValue());
                assertEquals("owner", db.scalar("select role from user where userId=?", admin));
                assertEquals("admin", db.scalar("select role from user where userId=1"));
                assertThrows(
                        PermissionErrorException.class,
                        db.component(AccountComponent.class).requestIdentity()::current);
                assertEquals(1, ((Number) db.scalar("select authVersion from user where userId=?", admin)).intValue());
            }
        }
    }
}
