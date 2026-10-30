package com.zrlog.admin.business.service;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.automation.AutomationEngine;
import com.zrlog.admin.business.automation.TaskAuthority;
import com.zrlog.admin.business.content.ArticlePublicationScheduler;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.Log;
import com.zrlog.model.User;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class ArticlePublicationScheduleTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() { return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"}); }
    private final String backend;
    public ArticlePublicationScheduleTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase db;
    private ContentComponent content;
    private AutomationComponent automations;
    private AccountAccess actor;
    private AdminAuditContext audit;
    private long later;

    @Before public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        db.execute("insert into type(typeId,typeName,alias) values(?,?,?)", 301, "Writing", "writing-301");
        db.loadAdminWebModules();
        content = db.component(ContentComponent.class); automations = db.component(AutomationComponent.class);
        actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        audit = new AdminAuditContext(1, "127.0.0.1", "/api/admin/article/create", "test");
        later = System.currentTimeMillis() + 3600000;
    }
    @After public void close() throws Exception { if (db != null) db.close(); }
    private <T extends CreateArticleRequest> T body(T request) {
        request.setTitle("Scheduled through publication"); request.setTypeId(301L);
        request.setContent("<p>Reviewed text</p>"); request.setEditorType("html"); return request;
    }
    private CreateArticleRequest scheduled() { var request = body(new CreateArticleRequest()); request.setPublishAt(later); return request; }
    private UpdateArticleRequest update(long id, int version) {
        var request = body(new UpdateArticleRequest()); request.setLogId((int) id); request.setVersion(version); return request;
    }
    private AutomationEngine engine() {
        return new AutomationEngine(automations.store(), new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())),
                () -> new User(db.dataSource())), automations.registry(), () -> later + 1000);
    }

    @Test public void futurePublicationSavesADraftAndRunsTheSameVersionBoundAssistantPlan() throws Exception {
        var request = scheduled(); request.setTransparentPublish(true);
        assertFalse(content.publishing().shouldUseTransparentPublishStream(request));
        var saved = content.publishing().create(actor, request, audit);
        assertTrue(saved.getRubbish()); assertFalse(saved.isPublicCacheRefreshRequired());
        assertEquals("scheduled", saved.getPublicationSchedule().state);
        var task = automations.store().task(saved.getPublicationSchedule().taskId).value;
        assertEquals("article.publish", task.type); assertEquals(Long.valueOf(later), task.schedule.at);
        assertEquals(saved.getVersion(), task.parameters.get("version").getAsInt());
        assertEquals(saved.getLogId().longValue(), task.parameters.get("articleId").getAsLong());
        assertEquals(1, engine().tick("external", 60000).executed);
        assertFalse(AccountAccess.truth(new Log(db.dataSource()).loadById(saved.getLogId()).get("rubbish")));
        assertEquals(0, engine().tick("external", 60000).executed);
    }

    @Test public void reschedulingUpdatesOnePlanAndImmediatePublicationPausesIt() throws Exception {
        var saved = content.publishing().create(actor, scheduled(), audit);
        var request = update(saved.getLogId(), saved.getVersion()); request.setPublishAt(later + 60000);
        var changed = content.publishing().update(actor, request, audit);
        assertEquals(saved.getPublicationSchedule().taskId, changed.getPublicationSchedule().taskId);
        assertEquals(1, automations.store().taskCount(actor.getUserId()));
        var task = automations.store().task(changed.getPublicationSchedule().taskId).value;
        assertEquals(changed.getVersion(), task.parameters.get("version").getAsInt());
        assertEquals(Long.valueOf(later + 60000), task.schedule.at);
        var immediate = update(changed.getLogId(), changed.getVersion()); immediate.setPublishAt(System.currentTimeMillis() - 1);
        var published = content.publishing().update(actor, immediate, audit);
        assertFalse(published.getRubbish()); assertNull(published.getPublicationSchedule());
        assertFalse(automations.store().task(task.id).value.enabled);
    }

    @Test public void laterDraftEditsInvalidateTheReviewedVersion() throws Exception {
        var saved = content.publishing().create(actor, scheduled(), audit);
        var edit = update(saved.getLogId(), saved.getVersion()); edit.setRubbish(true); edit.setContent("Changed after scheduling");
        content.publishing().update(actor, edit, audit);
        assertEquals(1, engine().tick("external", 60000).needsAttention);
        assertTrue(AccountAccess.truth(new Log(db.dataSource()).loadById(saved.getLogId()).get("rubbish")));
        assertFalse(automations.store().task(saved.getPublicationSchedule().taskId).value.enabled);
    }

    @Test public void articlePermissionsSufficeWithoutAnAdditionalAssistantGrant() throws Exception {
        var credential = actor.restrictActions(List.of("article.create", "article.read", "article.update", "article.publish"));
        var saved = content.publishing().create(credential, scheduled(), audit);
        assertEquals("scheduled", saved.getPublicationSchedule().state);
        assertTrue(saved.getRubbish());
    }

    @Test public void insufficientPermissionCannotCreateADraftOrPlanThroughTheFutureTimeField() throws Exception {
        long before = ((Number) db.scalar("select count(*) from log")).longValue();
        var restricted = actor.restrictActions(List.of("article.create", "article.read", "article.update"));
        assertThrows(PermissionErrorException.class, () -> content.publishing().create(restricted, scheduled(), audit));
        assertEquals(before, ((Number) db.scalar("select count(*) from log")).longValue());
        assertEquals(0, automations.store().taskCount(actor.getUserId()));
    }

    @Test public void schedulingFailureReturnsTheSavedDraftIdentityInsteadOfClaimingPublication() throws Exception {
        ArticlePublicationScheduler failing = new ArticlePublicationScheduler() {
            public Schedule schedule(AccountAccess actor, long id, int version, String title, long time, AdminAuditContext audit) {
                throw new IllegalStateException("controlled scheduling failure");
            }
            public void cancel(AccountAccess actor, long id, AdminAuditContext audit) { }
        };
        var publishing = new ArticlePublishingService(content.articles(), content.queries(), null, text -> text,
                new AdminAuditService(ApplicationServicesFixture.websiteKv(db.dataSource())), null, () -> failing,
                new AccountPermissionService(() -> new Log(db.dataSource())));
        var saved = publishing.create(actor, scheduled(), audit);
        assertTrue(saved.getRubbish()); assertNotNull(saved.getLogId()); assertEquals(0, saved.getVersion());
        assertEquals("failed", saved.getPublicationSchedule().state); assertNull(saved.getPublicationSchedule().taskId);
        assertTrue(AccountAccess.truth(new Log(db.dataSource()).loadById(saved.getLogId()).get("rubbish")));
        assertEquals(0, automations.store().taskCount(actor.getUserId()));
    }

    @Test public void immediatePublicationStillWorksAndFutureSchedulingCannotUnpublishALiveArticle() throws Exception {
        var live = content.publishing().create(actor, body(new CreateArticleRequest()), audit);
        assertFalse(live.getRubbish()); assertNull(live.getPublicationSchedule());
        var request = update(live.getLogId(), live.getVersion()); request.setPublishAt(later);
        assertThrows(com.zrlog.common.exception.ArgsException.class, () -> content.publishing().update(actor, request, audit));
        assertFalse(AccountAccess.truth(new Log(db.dataSource()).loadById(live.getLogId()).get("rubbish")));
        assertEquals(0, automations.store().taskCount(actor.getUserId()));
    }
}
