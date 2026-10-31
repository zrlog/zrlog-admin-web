package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.service.ScheduledArticleTaskProvider;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ScheduledPublicationTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() { return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"}); }
    private final String backend;
    public ScheduledPublicationTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase open() throws Exception {
        return backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
    }
    private final AdminAuditContext audit = new AdminAuditContext(1, "", "/automation/test", "Assistant");
    private static JsonObject parameters(int id, int version) {
        JsonObject args = new JsonObject();
        args.addProperty("articleId", id);
        args.addProperty("version", version);
        return args;
    }
    private int draft(ContentComponent content, AccountAccess actor) throws Exception {
        var body = new CreateArticleRequest();
        body.setTitle("Confirmed draft");
        body.setContent("<p>Confirmed content</p>");
        body.setEditorType("html");
        body.setRubbish(true);
        body.setTypeId(1L);
        body.setPreserveDraftAiMessages(true);
        return content.publishing().create(actor, body, audit).getLogId().intValue();
    }
    private TaskExecutionContext context(AccountAccess actor) {
        return new TaskExecutionContext("scheduled-publication", actor, System.currentTimeMillis() + 60000, System::currentTimeMillis, () -> {});
    }

    @Test
    public void publishesTheConfirmedVersionAndRecordsAnAuditWithoutHttpOrThreadIdentity() throws Exception {
        try (var db = open()) {
            var content = db.component(ContentComponent.class);
            var accounts = db.component(AccountComponent.class);
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            int id = draft(content, actor);
            AtomicInteger refreshed = new AtomicInteger();
            var provider = new ScheduledArticleTaskProvider(content.contentTools(), accounts.permissions(), ctx -> refreshed.incrementAndGet());
            JsonObject prepared = provider.prepare("article.publish", parameters(id, 0), actor);
            com.zrlog.admin.web.token.AdminTokenThreadLocal.remove();
            var result = provider.execute("article.publish", prepared, context(actor));
            assertEquals("success", result.status);
            assertTrue(result.notifyOwner);
            assertEquals("/article-edit?id=" + id, result.actionPath);
            assertFalse(AccountAccess.truth(db.scalar("SELECT rubbish FROM log WHERE logId=?", id)));
            assertEquals(1, ((Number) db.scalar("SELECT version FROM log WHERE logId=?", id)).intValue());
            assertEquals(1, refreshed.get());
            assertTrue(db.scalar("SELECT value FROM website WHERE name='admin_audit_log'").toString().contains("PUBLISH_ARTICLE"));
        }
    }

    @Test
    public void editingAfterConfirmationStopsPublicationUntilThePlanIsUpdated() throws Exception {
        try (var db = open()) {
            var content = db.component(ContentComponent.class);
            var accounts = db.component(AccountComponent.class);
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            int id = draft(content, actor);
            var provider = new ScheduledArticleTaskProvider(content.contentTools(), accounts.permissions(), ctx -> {});
            JsonObject prepared = provider.prepare("article.publish", parameters(id, 0), actor);
            var edit = new UpdateArticleRequest();
            edit.setLogId(id); edit.setVersion(0); edit.setTitle("Unconfirmed changes"); edit.setContent("New content");
            edit.setEditorType("html"); edit.setTypeId(1L); edit.setRubbish(true);
            content.publishing().update(actor, edit, audit);
            assertEquals("needs_attention", provider.execute("article.publish", prepared, context(actor)).status);
            assertTrue(AccountAccess.truth(db.scalar("SELECT rubbish FROM log WHERE logId=?", id)));
            assertThrows(IllegalArgumentException.class, () -> provider.prepare("article.publish", prepared, actor));
            assertEquals("success", provider.execute("article.publish", provider.prepare("article.publish", parameters(id, 1), actor), context(actor)).status);
        }
    }

    @Test
    public void refreshFailurePreservesPublicationAndMakesFollowupVisible() throws Exception {
        try (var db = open()) {
            var content = db.component(ContentComponent.class);
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            int id = draft(content, actor);
            var provider = new ScheduledArticleTaskProvider(content.contentTools(), db.component(AccountComponent.class).permissions(), ctx -> {throw new IllegalStateException("offline");});
            var result = provider.execute("article.publish", provider.prepare("article.publish", parameters(id, 0), actor), context(actor));
            assertEquals("needs_attention", result.status);
            assertFalse(AccountAccess.truth(db.scalar("SELECT rubbish FROM log WHERE logId=?", id)));
            assertEquals("admin.assistant.error.publicationRefreshFailed", result.messageKey);
        }
    }
}
