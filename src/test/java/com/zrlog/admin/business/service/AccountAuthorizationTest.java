package com.zrlog.admin.business.service;

import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AccountAuthorizationTest {
    static void login(InMemoryZrLogDatabase db, int id, String role) throws Exception {
        db.execute("update user set role=? where userId=?", role, id);
        AdminTokenVO token = new AdminTokenVO(); token.setUserId(id); token.setSessionId("session-"+id);
        token.setAuthVersion(((Number) db.scalar("select authVersion from user where userId=?", id)).intValue());
        java.lang.reflect.Method setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true); AdminTokenThreadLocal.remove(); setter.invoke(null, token);
    }
    static AccountAccess account(String role) {
        return AccountAccess.from(Map.of("userId",1,"role",role,"enabled",true,"authVersion",0));
    }
    @Test public void privateAndDraftScopesMustIntersectCurrentRoleAndOwnership() {
        for (String role : AccountAccess.ROLES) {
            AccountAccess a=account(role);
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read"),1,false,true));
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read","articles:read_private"),1,true,true));
            assertTrue(ArticleAccess.canRead(a, a.scopes(),1,true,true));
            assertEquals(a.isAdministrator(), ArticleAccess.canRead(a,a.scopes(),2,true,true));
            assertEquals(a.managesAllArticles(), ArticleAccess.canRead(a,a.scopes(),2,true,false));
            assertFalse(ArticleAccess.canRead(a, Set.of("articles:read"),2,false,false));
            assertFalse(ArticleAccess.canWrite(a, Set.of("articles:write"),1,false,false,false,false));
            assertEquals(a.canPublish(), ArticleAccess.canWrite(a,a.scopes(),1,true,false,false,false));
        }
        assertFalse(AccountAction.MEMBER_MANAGE.allowed(account("editor")));
        assertFalse(AccountAction.ADMIN_APPOINT.allowed(account("admin")));
        assertFalse(AccountAction.ARTICLE_DELETE.allowed(account("contributor")));
        assertFalse(ArticleAccess.canRead(AccountAccess.from(null),Set.of("articles:read"),1,false,false));
    }
    @Test public void everyRegisteredAdminRouteRequiresAnExplicitAction() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            ServerConfig config=new ServerConfig();
            AdminRouters.configAdminRoute(config.getRouter(),AdminConstants.adminResource,"");
            assertFalse(config.getRouter().getRouterMap().containsKey("/api/admin/knowledge/chat"));
            assertNotNull(config.getRouter().getMethod("/api/admin/article/ai", com.hibegin.http.HttpMethod.POST));
            Set<String> ids=new HashSet<>();
            for(AccountAction action:AccountAction.values()) assertTrue(ids.add(action.getId()));
            config.getRouter().getRouterMap().forEach((path,method)-> {
                if(path.contains("admin")) {
                    RequiresAction binding = method.getAnnotation(RequiresAction.class);
                    assertNotNull(path+" -> "+method, binding);
                    assertFalse(path + " missing description", binding.descriptionKey().isBlank());
                }
            });
            assertThrows(PermissionErrorException.class,()->AccountPermissionService.checkRoute(Object.class.getMethod("toString"),null));
        }
    }
    @Test public void articleServiceMustPreserveAuthorAndRejectForeignPrivateAndPublishedWrites() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            db.execute("insert into user(userId,userName,role) values(?,?,?)",2,"writer","author");
            login(db,1,"owner");
            AdminArticleService service=new AdminArticleService();
            CreateArticleRequest create=new CreateArticleRequest();create.setTitle("Private");create.setTypeId(1L);create.setPrivacy(true);create.setRubbish(true);create.setContent("secret");
            long id=service.create(AdminTokenThreadLocal.getUser(),create).getLogId();
            login(db,2,"editor");
            assertThrows(PermissionErrorException.class,()->AccountPermissionService.readArticle(id));
            assertThrows(PermissionErrorException.class,()->new ArticleVersionService().listVersions((int)id));
            db.execute("update log set privacy=? where logId=?",false,id);
            UpdateArticleRequest update=new UpdateArticleRequest();update.setLogId((int)id);update.setTitle("Edited");update.setTypeId(1L);update.setVersion(1);update.setRubbish(true);update.setContent("edited");
            service.update(AdminTokenThreadLocal.getUser(),update);
            assertEquals(1,((Number)db.scalar("select userId from log where logId=?",id)).intValue());
            login(db,2,"author");
            assertThrows(PermissionErrorException.class,()->service.update(AdminTokenThreadLocal.getUser(),update));
            assertThrows(PermissionErrorException.class,()->service.delete(id));
            login(db,2,"contributor");create.setPrivacy(false);create.setRubbish(false);
            assertThrows(PermissionErrorException.class,()->service.create(AdminTokenThreadLocal.getUser(),create));
            create.setRubbish(true);long own=service.create(AdminTokenThreadLocal.getUser(),create).getLogId();
            assertThrows(PermissionErrorException.class,()->service.delete(own));
            db.execute("update user set authVersion=authVersion+1 where userId=2");
            assertThrows(PermissionErrorException.class,AccountPermissionService::current);
        }
    }
    @Test public void membersCannotEscalateOrModifyOwnerAndTransferRevokesBothSessions() throws Exception {
        for (String backend : List.of("h2", "sqlite", "webapi")) {
            try(InMemoryZrLogDatabase db="webapi".equals(backend) ? InMemoryZrLogDatabase.openWebApi() : "sqlite".equals(backend) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open()) {
                login(db,1,"owner");MemberService service=new MemberService();
                MemberModels.Create create=new MemberModels.Create();create.userName="second";create.password="a-long-new-password";create.role="admin";
                int admin=service.create(create).userId;
                assertFalse(String.valueOf(db.scalar("select password from user where userId=?",admin)).contains(create.password));
                login(db,admin,"admin");
                assertThrows(PermissionErrorException.class,()->service.create(create));
                MemberModels.Update update=new MemberModels.Update();update.userId=1;update.role="author";update.enabled=false;
                assertThrows(PermissionErrorException.class,()->service.update(update));
                login(db,1,"owner");
                db.execute("update user set password=? where userId=1",com.hibegin.common.util.PasswordHashUtils.hash(com.hibegin.common.util.SecurityUtils.md5("owner-password")));
                MemberModels.Transfer transfer=new MemberModels.Transfer();transfer.userId=admin;transfer.password="owner-password";service.transfer(transfer);
                assertEquals(1,((Number)db.scalar("select count(*) from user where role='owner'")).intValue());
                assertEquals("owner",db.scalar("select role from user where userId=?",admin));
                assertEquals("admin",db.scalar("select role from user where userId=1"));
                assertThrows(PermissionErrorException.class,AccountPermissionService::current);
                assertEquals(1,((Number)db.scalar("select authVersion from user where userId=?",admin)).intValue());
            }
        }
    }
}
