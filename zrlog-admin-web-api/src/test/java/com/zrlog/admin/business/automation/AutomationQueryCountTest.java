package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.repository.UserRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Count actual database calls through the production graph, without waiting for resident heartbeats. */
public class AutomationQueryCountTest {
    private InMemoryZrLogDatabase db;
    private AutomationComponent component;
    private AccountAccess actor;
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicInteger retentionScans = new AtomicInteger();

    @Before public void open() throws Exception {
        db = InMemoryZrLogDatabase.openWebApi();
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        component = db.component(AutomationComponent.class);
        component.plugin().stop();
        actor = AccountAccess.from(new UserRepository(db.dataSource()).loadById(1L));
        component.defaults().initialize();
        component.defaults().initializePluginTrigger();
        db.execute("UPDATE website SET remark=? WHERE name>=? AND name<?", "~", AutomationStore.TASK_PREFIX, "assistant.v1.task;");
        component.ticks().tick("internal", null, 660000);
        db.afterWebApiQuery((sql, params) -> {
            // INSERT RETURNING is counted once by the write hook, not again as a read request.
            if (!sql.startsWith("SELECT")) return;
            reads.incrementAndGet();
            if (sql.contains("ORDER BY name DESC LIMIT ?") && java.util.Arrays.asList(params).contains(1000)) retentionScans.incrementAndGet();
        });
        db.beforeWebApiUpdate((sql, params) -> writes.incrementAndGet());
    }

    @After public void close() throws Exception { if (db != null) db.close(); }

    private void reset() { reads.set(0); writes.set(0); retentionScans.set(0); }
    private AutomationModels.TickResult tick() throws Exception {
        assertTrue(component.settings().internalEnabled());
        return component.ticks().tick("internal", null, 660000);
    }

    @Test public void emptyTicksBatchStateAndDoNotLoadUnrelatedRuntimeSettings() throws Exception {
        assertEquals(0, tick().executed);
        assertEquals(3, reads.get());
        assertEquals(2, writes.get());
        reset();
        component.registry().require("assistant.reminder");
        assertEquals(0, reads.get());
        var token = component.settings().rotate(actor, 0);
        reset();
        var authorization = component.settings().authenticateTrigger("Bearer " + token.token);
        assertEquals(0, component.ticks().tick("external", authorization.credentialId(), authorization.budgetMillis()).executed);
        assertEquals(3, reads.get());
        component.settings().revoke(actor, token.settings.revision);
        assertThrows(PermissionErrorException.class, () -> component.settings().authenticateTrigger("Bearer " + token.token));
    }

    @Test public void reminderRunsReuseTheirOwnWritesAndPruneEachOwnerOncePerTick() throws Exception {
        reminder();
        reset();
        assertEquals(1, tick().executed);
        assertEquals(9, reads.get());
        assertEquals(6, writes.get());
        assertEquals(1, retentionScans.get());
        reminder(); reminder();
        reset();
        assertEquals(2, tick().executed);
        assertEquals(13, reads.get());
        assertEquals(10, writes.get());
        assertEquals(1, retentionScans.get());
    }

    @Test public void sharedNoticesReadAcknowledgementsInOneBatchAndKeepAccountIsolation() throws Exception {
        var authority = new TaskAuthority(new AccountIdentityService(() -> new UserRepository(db.dataSource())), () -> new UserRepository(db.dataSource()));
        var notices = new AutomationNotificationService(component.store(), authority, component.tasks());
        reset();
        assertTrue(notices.list(actor).isEmpty());
        assertEquals(7, reads.get());
        for (int i = 0; i < 50; i++) {
            var run = new AutomationModels.Run();
            long now = System.currentTimeMillis();
            run.id = AutomationStore.ownerPrefix(0) + AutomationStore.timeKey(now + i) + "." + UUID.randomUUID();
            run.ownerId = 0; run.taskId = AutomationDefaults.systemId("system.check-version");
            run.status = "failed"; run.startedAt = now; run.finishedAt = now; run.taskName = "Version check";
            component.store().insertRun(run);
        }
        reset();
        var unread = notices.list(actor);
        assertEquals(50, unread.size());
        assertEquals(8, reads.get());
        for (var notice : unread) assertTrue(notices.markRead(actor, notice.getTaskKey()));
        reset();
        assertTrue(notices.list(actor).isEmpty());
        assertEquals(8, reads.get());
        db.execute("INSERT INTO user(userId,userName,role) VALUES(?,?,?)", 2, "other-admin", "admin");
        var other = AccountAccess.from(new UserRepository(db.dataSource()).loadById(2L));
        assertEquals(50, notices.list(other).size());
    }

    private void reminder() throws Exception {
        var task = new StoredTask();
        task.id = AutomationStore.ownerPrefix(1) + UUID.randomUUID(); task.ownerId = 1; task.revision = 1L;
        task.name = "Reminder"; task.type = "assistant.reminder"; task.enabled = true;
        task.nextRunAt = System.currentTimeMillis() - 1000;
        task.schedule = new AutomationModels.Schedule(); task.schedule.kind = "once"; task.schedule.at = task.nextRunAt;
        task.parameters.addProperty("text", "Review draft"); task.executionActions = List.of("account.session");
        component.store().insertTask(task, actor);
    }
}
