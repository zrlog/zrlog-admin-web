package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.AIArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ai.AIComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
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
public class ArticleActorBoundaryTest {
    private final boolean sqlite;

    public ArticleActorBoundaryTest(boolean sqlite) {
        this.sqlite = sqlite;
    }

    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() {
        return new Boolean[]{false, true};
    }

    private static AdminTokenVO token(int userId) {
        var token = new AdminTokenVO();
        token.setUserId(userId);
        return token;
    }

    private static CreateArticleRequest article(String title) {
        var request = new CreateArticleRequest();
        request.setTitle(title);
        request.setContent("<p>Original body</p>");
        request.setTypeId(1L);
        request.setRubbish(true);
        return request;
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUri")) return "/admin/article-edit";
                    if (method.getName().equals("getContextPath")) return "";
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    return null;
                });
    }

    @Test
    public void explicitAuthorOwnsArticleHistoryAndAssistantDataWithoutARequestIdentity() throws Exception {
        try (var db = open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.loadAdminWebModules();
            var accounts = db.component(AccountComponent.class);
            var content = db.component(ContentComponent.class);
            var conversations = db.component(AIComponent.class).conversations();
            var author = accounts.identities().account(token(2));
            conversations.captureAccount(1).saveAIMessage(
                    List.of(new AIResponseEntry.AIContentEntry("user", "administrator draft")), -1L);
            conversations.captureAccount(2).saveAIMessage(
                    List.of(new AIResponseEntry.AIContentEntry("user", "author draft")), -2L);
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            long id = content.articles().create(author, article("Original")).getLogId();
            assertEquals(2, ((Number) db.scalar("select userId from log where logId=?", id)).intValue());
            AdminTokenThreadLocal.remove();
            var editor = (AIArticleGlobalResponse) content.queries()
                    .loadDetailById(author, Long.toString(id), request()).getData();
            assertEquals("author draft", editor.getAiMessages().get(0).getContent());
            assertEquals("administrator draft", conversations.captureAccount(1)
                    .getAiMessageInfoByArticleId(-1L).getAiMessages().get(0).getContent());
            var update = new UpdateArticleRequest();
            update.setLogId((int) id);
            update.setVersion(0);
            update.setTitle("Changed");
            update.setContent("<p>Changed body</p>");
            update.setTypeId(1L);
            update.setRubbish(true);
            content.articles().update(author, update);
            assertEquals(2, content.versions().listVersions(author, (int) id).size());
            assertTrue(content.revisions().compare(author, (int) id, 0, 1, request())
                    .getChangedFields().contains("title"));
            var rollback = new ArticleVersionRollbackRequest();
            rollback.setLogId((int) id);
            rollback.setVersion(1);
            rollback.setTargetVersion(0);
            content.revisions().rollback(author, rollback, request());
            assertEquals("Original", db.scalar("select title from log where logId=?", id));
            assertEquals(2, ((Number) db.scalar("select version from log where logId=?", id)).intValue());
            assertTrue(content.articles().delete(author, id));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void writerRevalidatesActorsAndKeepsTheirDelegationBounds() throws Exception {
        try (var db = open()) {
            db.loadAdminWebModules();
            var identities = db.component(AccountComponent.class).identities();
            var content = db.component(ContentComponent.class);
            var actor = identities.account(token(1));
            var readOnly = actor.restrictActions(Set.of(AccountAction.ARTICLE_READ.getId()));
            AdminTokenThreadLocal.remove();
            assertFalse(AccountAction.ARTICLE_CREATE.allowed(identities.revalidate(readOnly)));
            assertThrows(PermissionErrorException.class,
                    () -> content.articles().create(readOnly, article("Denied by delegation")));
            db.execute("update user set role='author' where userId=1");
            assertThrows(PermissionErrorException.class, () -> identities.revalidate(actor));
            assertThrows(PermissionErrorException.class,
                    () -> content.articles().create(actor, article("Stale role")));
            var author = identities.account(token(1));
            long id = content.articles().create(author, article("Allowed after reauthentication")).getLogId();
            db.execute("update user set authVersion=1 where userId=1");
            assertThrows(PermissionErrorException.class, () -> content.articles().delete(author, id));
            assertEquals(1, ((Number) db.scalar("select count(*) from log where logId=?", id)).intValue());
            assertThrows(PermissionErrorException.class,
                    () -> content.articles().create(author, article("Stale credentials")));
            assertEquals(1, ((Number) db.scalar("select count(*) from log")).intValue());
        }
    }

    private InMemoryZrLogDatabase open() throws Exception {
        return sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }
}
