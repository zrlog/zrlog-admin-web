package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AutomationNotificationTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }

    private final String backend;
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private TaskAuthority authority;
    private AccountAccess owner;
    private AccountAccess author;

    public AutomationNotificationTest(String backend) { this.backend = backend; }

    @Before public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        store = new AutomationStore(() -> new WebSite(db.dataSource()));
        authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        owner = AccountAccess.from(new User(db.dataSource()).loadById(1));
        db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "author", "author");
        author = AccountAccess.from(new User(db.dataSource()).loadById(2));
    }

    @After public void close() throws Exception { if (db != null) db.close(); }

    private AutomationNotificationService inbox() { return new AutomationNotificationService(store, authority); }

    @Test public void reminderSurvivesServiceRecreationAndTaskDeletionAndReadDoesNotDeleteHistory() throws Exception {
        var registry = new TaskRegistry(() -> List.of(new ReminderTaskProvider()));
        var tasks = new AutomationService(store, authority, registry, System::currentTimeMillis);
        var plan = new AutomationModels.Plan();
        plan.name = "Check the draft";
        plan.type = "assistant.reminder";
        plan.schedule = new AutomationModels.Schedule();
        plan.schedule.at = System.currentTimeMillis() + 60000;
        plan.parameters.addProperty("text", "My private reminder");
        var task = tasks.save(author, plan);
        var run = new AutomationEngine(store, authority, registry, System::currentTimeMillis)
                .runNow(author, task.id, task.revision, 10000);
        tasks.delete(author, task.id, store.task(task.id).value.revision);
        var notices = inbox().list(author);
        assertEquals(1, notices.size());
        assertTrue(inbox().list(owner).isEmpty());
        assertTrue(AutomationStore.JSON.toJson(notices).contains("My private reminder"));
        assertFalse(AutomationStore.JSON.toJson(notices).contains("executionActions"));
        var payload = (com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse.OperationTaskPayload) notices.get(0).getPayload();
        assertEquals("/assistant?runId=" + run.id, payload.getActionPath());
        assertTrue(inbox().markRead(author, notices.get(0).getTaskKey()));
        assertTrue(inbox().markRead(author, notices.get(0).getTaskKey()));
        assertTrue(inbox().list(author).isEmpty());
        assertEquals("My private reminder", store.run(run.id).value.result.text);
        assertEquals("success", store.run(run.id).value.status);
        assertNotNull(store.run(run.id).value.noticeReadAt);
    }

    @Test public void accountIsolationAndCurrentAuthorityApplyToListingAndAcknowledgement() throws Exception {
        var run = record(2, "success", true);
        assertEquals(1, inbox().list(author).size());
        String key = AutomationNotificationService.PREFIX + run.id;
        assertThrows(PermissionErrorException.class, () -> inbox().markRead(owner, key));
        assertNull(store.run(run.id).value.noticeReadAt);
        assertThrows(PermissionErrorException.class, () -> inbox().list(author.restrictActions(List.of("article.read"))));
        assertThrows(PermissionErrorException.class, () -> inbox().markRead(author.restrictActions(List.of("article.read")), key));
        db.execute("update user set enabled=? where userId=?", false, 2);
        assertThrows(PermissionErrorException.class, () -> inbox().list(author));
        assertThrows(PermissionErrorException.class, () -> inbox().markRead(author, key));
        assertNull(store.run(run.id).value.noticeReadAt);
    }

    @Test public void failuresAppearWithoutSuccessOptInAndRunningOrQuietMaintenanceDoesNot() throws Exception {
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

    @Test public void inboxIsBoundedAndReadNoticesAllowOlderUnreadResultsToAppear() throws Exception {
        for (int i = 0; i < 55; i++) record(1, "success", true);
        var first = inbox().list(owner);
        assertEquals(50, first.size());
        for (var notice : first) inbox().markRead(owner, notice.getTaskKey());
        assertEquals(5, inbox().list(owner).size());
        assertEquals(55, store.runCount(1, null));
    }

    private AutomationModels.Run record(int ownerId, String status, boolean notify) throws Exception {
        var run = new AutomationModels.Run();
        long now = System.currentTimeMillis();
        run.id = AutomationStore.ownerPrefix(ownerId) + AutomationStore.timeKey(now) + "." + UUID.randomUUID();
        run.taskId = AutomationStore.ownerPrefix(ownerId) + UUID.randomUUID();
        run.ownerId = ownerId;
        run.taskName = "Task";
        run.startedAt = now;
        run.finishedAt = "running".equals(status) ? null : now;
        run.status = status;
        run.result = AutomationModels.Result.success("admin.assistant.reminder.success.completed");
        run.result.notifyOwner = notify;
        store.insertRun(run);
        return run;
    }
}
