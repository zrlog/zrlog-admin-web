package com.zrlog.admin.business.automation;

import static org.junit.Assert.*;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.security.AccountAccess;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AutomationNotificationTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(
                new Object[] {"h2"}, new Object[] {"sqlite"}, new Object[] {"web-api"});
    }

    private final String backend;
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private TaskAuthority authority;
    private AccountAccess owner;
    private AccountAccess author;

    public AutomationNotificationTest(String backend) {
        this.backend = backend;
    }

    @Before
    public void open() throws Exception {
        db =
                backend.equals("sqlite")
                        ? InMemoryZrLogDatabase.openSqlite()
                        : backend.equals("web-api")
                                ? InMemoryZrLogDatabase.openWebApi()
                                : InMemoryZrLogDatabase.open();
        store =
                new AutomationStore(
                        () -> new WebSiteRepository(db.dataSource()),
                        () ->
                                new com.zrlog.admin.data.repository.AssistantTaskRepository(
                                        db.dataSource()),
                        () ->
                                new com.zrlog.admin.data.repository.AssistantTaskRunRepository(
                                        db.dataSource()),
                        () ->
                                new com.zrlog.admin.data.repository.AssistantNoticeReadRepository(
                                        db.dataSource()));
        authority =
                new TaskAuthority(
                        new AccountIdentityService(
                                () ->
                                        new com.zrlog.admin.data.repository
                                                .AccountIdentityRepository(db.dataSource())),
                        () ->
                                new com.zrlog.admin.data.repository.AccountIdentityRepository(
                                        db.dataSource()));
        owner = AccountAccess.from(new UserRepository(db.dataSource()).loadById(1L));
        db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "author", "author");
        author = AccountAccess.from(new UserRepository(db.dataSource()).loadById(2L));
    }

    @After
    public void close() throws Exception {
        if (db != null) db.close();
    }

    private AutomationNotificationService inbox() {
        return new AutomationNotificationService(store, authority);
    }

    @Test
    public void reminderSurvivesServiceRecreationAndTaskDeletionAndReadDoesNotDeleteHistory()
            throws Exception {
        var registry = new TaskRegistry(() -> List.of(new ReminderTaskProvider()));
        var tasks = new AutomationService(store, authority, registry, System::currentTimeMillis);
        var plan = new AutomationModels.Plan();
        plan.name = "Check the draft";
        plan.type = "assistant.reminder";
        plan.schedule = new AutomationModels.Schedule();
        plan.schedule.at = System.currentTimeMillis() + 60000;
        plan.parameters.addProperty("text", "My private reminder");
        var task = tasks.save(author, plan);
        var run =
                new AutomationEngine(store, authority, registry, System::currentTimeMillis)
                        .runNow(author, task.id, task.revision, 10000);
        tasks.delete(author, task.id, store.task(task.id).value.revision);
        var notices = inbox().list(author);
        assertEquals(1, notices.size());
        assertTrue(inbox().list(owner).isEmpty());
        assertTrue(AutomationStore.JSON.toJson(notices).contains("My private reminder"));
        assertFalse(AutomationStore.JSON.toJson(notices).contains("executionActions"));
        var payload =
                (com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse
                                .OperationTaskPayload)
                        notices.get(0).getPayload();
        assertEquals("/assistant/history?runId=" + run.id, payload.getActionPath());
        assertTrue(inbox().markRead(author, notices.get(0).getTaskKey()));
        assertTrue(inbox().markRead(author, notices.get(0).getTaskKey()));
        assertTrue(inbox().list(author).isEmpty());
        assertEquals("My private reminder", store.run(run.id).value.result.text);
        assertEquals("success", store.run(run.id).value.status);
        assertEquals(
                1L,
                ((Number)
                                db.scalar(
                                        "SELECT count(*) FROM assistant_notice_read WHERE runId=? AND userId=?",
                                        run.id,
                                        author.getUserId()))
                        .longValue());
    }

    @Test
    public void accountIsolationAndCurrentAuthorityApplyToListingAndAcknowledgement()
            throws Exception {
        var run = record(2, "success", true);
        assertEquals(1, inbox().list(author).size());
        String key = AutomationNotificationService.PREFIX + run.id;
        assertThrows(PermissionErrorException.class, () -> inbox().markRead(owner, key));
        assertEquals(
                0L,
                ((Number)
                                db.scalar(
                                        "SELECT count(*) FROM assistant_notice_read WHERE runId=?",
                                        run.id))
                        .longValue());
        assertThrows(
                PermissionErrorException.class,
                () -> inbox().list(author.restrictActions(List.of("article.read"))));
        assertThrows(
                PermissionErrorException.class,
                () -> inbox().markRead(author.restrictActions(List.of("article.read")), key));
        db.execute("update user set enabled=? where userId=?", false, 2);
        assertThrows(PermissionErrorException.class, () -> inbox().list(author));
        assertThrows(PermissionErrorException.class, () -> inbox().markRead(author, key));
        assertEquals(
                0L,
                ((Number)
                                db.scalar(
                                        "SELECT count(*) FROM assistant_notice_read WHERE runId=?",
                                        run.id))
                        .longValue());
    }

    @Test
    public void failuresAppearWithoutSuccessOptInAndRunningOrQuietMaintenanceDoesNot()
            throws Exception {
        record(1, "success", false);
        record(1, "running", true);
        record(1, "failed", false);
        record(1, "needs_attention", false);
        record(1, "uncertain", false);
        var notices = inbox().list(owner);
        assertEquals(3, notices.size());
        assertEquals(1, notices.stream().filter(n -> "error".equals(n.getStatus())).count());
        assertEquals(2, notices.stream().filter(n -> "warning".equals(n.getStatus())).count());
    }

    @Test
    public void inboxIsBoundedAndReadNoticesAllowOlderUnreadResultsToAppear() throws Exception {
        for (int i = 0; i < 55; i++) record(1, "success", true);
        var first = inbox().list(owner);
        assertEquals(50, first.size());
        for (var notice : first) inbox().markRead(owner, notice.getTaskKey());
        assertEquals(5, inbox().list(owner).size());
        assertEquals(55, store.runCount(1, null));
    }

    @Test
    public void oldFailureRemainsVisibleBehindMoreThanAThousandQuietRunsAndRequiresManualRemoval()
            throws Exception {
        var failure = record(1, "failed", false);
        var before = store.run(failure.id);
        failure.startedAt = System.currentTimeMillis() - java.time.Duration.ofDays(120).toMillis();
        failure.finishedAt = failure.startedAt;
        assertTrue(store.replaceRun(before, failure));
        for (int i = 0; i < 1100; i++) record(1, "success", false);
        var notices = inbox().list(owner);
        assertEquals(1, notices.size());
        assertEquals(
                AutomationNotificationService.PREFIX + failure.id, notices.get(0).getTaskKey());
        assertEquals(0, store.pruneRuns(1, System.currentTimeMillis()));
        var service =
                new AutomationService(
                        store, authority, new TaskRegistry(List::of), System::currentTimeMillis);
        assertThrows(
                PermissionErrorException.class,
                () -> service.deleteRun(author, failure.id, failure.version));
        assertThrows(IllegalStateException.class, () -> service.deleteRun(owner, failure.id, 0));
        service.deleteRun(owner, failure.id, failure.version);
        assertNull(store.run(failure.id));
        assertTrue(inbox().list(owner).isEmpty());
    }

    @Test
    public void listsReadSummariesAndDetailsKeepTheFullPlanAndChildResults() throws Exception {
        var run = record(1, "success", true);
        var before = store.run(run.id);
        run.planSnapshot = new AutomationModels.Plan();
        run.planSnapshot.parameters.addProperty("privateDetail", "plan-detail-only");
        run.result.text = "summary " + "x".repeat(12000) + "full-result-tail";
        var child = new AutomationModels.ChildResult();
        child.id = "nested";
        child.text = "child-detail-only";
        run.result.children.add(child);
        assertTrue(store.replaceRun(before, run));
        var list = store.visibleRuns(1, List.of(), null, 20, 0);
        assertEquals(1, list.size());
        assertNull(list.get(0).value.planSnapshot);
        assertEquals(1000, list.get(0).value.result.text.length());
        assertTrue(list.get(0).value.result.children.isEmpty());
        assertFalse(AutomationStore.JSON.toJson(inbox().list(owner)).contains("full-result-tail"));
        var detail = store.run(run.id).value;
        assertEquals(
                "plan-detail-only",
                detail.planSnapshot.parameters.get("privateDetail").getAsString());
        assertTrue(detail.result.text.endsWith("full-result-tail"));
        assertEquals("child-detail-only", detail.result.children.get(0).text);
        assertEquals(run.version, detail.version);
    }

    private AutomationModels.Run record(int ownerId, String status, boolean notify)
            throws Exception {
        var run = new AutomationModels.Run();
        long now = System.currentTimeMillis();
        run.id =
                AutomationStore.ownerPrefix(ownerId)
                        + AutomationStore.timeKey(now)
                        + "."
                        + UUID.randomUUID();
        run.taskId = AutomationStore.ownerPrefix(ownerId) + UUID.randomUUID();
        run.ownerId = ownerId;
        run.taskName = "Task";
        run.startedAt = now;
        run.finishedAt = "running".equals(status) ? null : now;
        run.status = status;
        run.result = AutomationModels.Result.success("admin.assistant.reminder.success.completed");
        run.result.notifyOwner = notify;
        if (run.taskName == null) run.taskName = "Task";
        if (run.taskType == null) run.taskType = "assistant.reminder";
        if (run.trigger == null) run.trigger = "internal";
        store.insertRun(run);
        return run;
    }
}
