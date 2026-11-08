package com.zrlog.admin.business.automation;

import static org.junit.Assert.*;

import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.WebSiteRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutomationTickServiceTest {
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private AutomationEngine engine;
    private final AtomicLong now = new AtomicLong(1_790_000_000_000L);

    @Before
    public void open() throws Exception {
        db = InMemoryZrLogDatabase.openWebApi();
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
        var authority =
                new TaskAuthority(
                        new AccountIdentityService(
                                () ->
                                        new com.zrlog.admin.data.repository
                                                .AccountIdentityRepository(db.dataSource())),
                        () ->
                                new com.zrlog.admin.data.repository.AccountIdentityRepository(
                                        db.dataSource()));
        engine = new AutomationEngine(store, authority, new TaskRegistry(List::of), now::get);
    }

    @After
    public void close() throws Exception {
        if (db != null) db.close();
    }

    @Test
    public void olderCompletionCannotOverwriteANewerTicksHealth() throws Exception {
        var newer = new AutomationTickService(engine, store, now::get);
        var newestId = new AtomicReference<String>();
        var older =
                new AutomationTickService(
                        engine,
                        store,
                        now::get,
                        null,
                        (budget, receipt) -> {
                            now.incrementAndGet();
                            newer.tick("external", "new-credential", budget);
                            newestId.set(newer.health("external").id);
                        });
        older.tick("external", "old-credential", 60000);
        var health = older.health("external");
        assertEquals(newestId.get(), health.id);
        assertEquals("new-credential", health.credentialId);
        assertEquals("success", health.status);
    }

    @Test
    public void failedInitialHealthClaimCannotFinalizeAnotherTicksRecord() throws Exception {
        var ticks = new AutomationTickService(engine, store, now::get);
        ticks.tick("internal", null, 60000);
        var raced = new AtomicBoolean();
        var newestId = new AtomicReference<String>();
        var initialId = ticks.health("internal").id;
        db.beforeWebApiUpdate(
                (sql, args) -> {
                    if (sql.startsWith("UPDATE `website` SET `value`=?")
                            && "assistant.v1.tick:internal".equals(args[2])
                            && raced.compareAndSet(false, true)) {
                        now.incrementAndGet();
                        ticks.tick("internal", null, 60000);
                        newestId.set(ticks.health("internal").id);
                    }
                });
        ticks.tick("internal", null, 60000);
        assertTrue(raced.get());
        var health = ticks.health("internal");
        assertNotEquals(initialId, health.id);
        assertEquals(newestId.get(), health.id);
        assertEquals("success", health.status);
    }

    @Test
    public void retentionIsDailySharedAndBoundedWithoutPruningFailures() throws Exception {
        var scans = new AtomicInteger();
        db.afterWebApiQuery(
                (sql, args) -> {
                    if (sql.startsWith("SELECT r.id,r.version FROM assistant_task_run"))
                        scans.incrementAndGet();
                });
        var ticks = new AutomationTickService(engine, store, now::get);
        ticks.tick("internal", null, 60000);
        assertEquals(0, scans.get());
        for (int i = 0; i < 65; i++) oldRun("success");
        var failed = oldRun("failed");
        var unresolved = oldRun("uncertain");
        ticks.tick("external", "test", 60000);
        assertEquals(0, scans.get());
        assertEquals(67, store.runCount(1, null));

        now.addAndGet(Duration.ofDays(1).toMillis());
        ticks.tick("external", "test", 60000);
        assertEquals(1, scans.get());
        assertEquals(3, store.runCount(1, null));
        ticks.tick("internal", null, 60000);
        assertEquals(1, scans.get());

        now.addAndGet(60000);
        ticks.tick("internal", null, 60000);
        assertEquals(2, scans.get());
        assertEquals(2, store.runCount(1, null));
        assertNotNull(store.run(failed));
        assertNotNull(store.run(unresolved));
        now.addAndGet(60000);
        ticks.tick("external", "test", 60000);
        assertEquals(2, scans.get());
    }

    private String oldRun(String status) throws Exception {
        long finished = now.get() - Duration.ofDays(31).toMillis();
        var run = new AutomationModels.Run();
        run.id =
                AutomationStore.ownerPrefix(1)
                        + AutomationStore.timeKey(finished)
                        + "."
                        + UUID.randomUUID();
        run.ownerId = 1;
        run.taskId = AutomationStore.ownerPrefix(1) + UUID.randomUUID();
        run.taskName = "Reminder";
        run.taskType = "assistant.reminder";
        run.trigger = "internal";
        run.startedAt = finished - 1000;
        run.finishedAt = finished;
        run.status = status;
        store.insertRun(run);
        return run.id;
    }
}
