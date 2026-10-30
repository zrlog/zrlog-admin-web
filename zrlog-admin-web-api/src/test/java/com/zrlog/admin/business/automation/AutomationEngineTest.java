package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AutomationEngineTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }

    private final String backend;
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private TaskAuthority authority;
    private TaskRegistry registry;
    private AutomationService service;
    private AccountAccess actor;
    private final AtomicLong now = new AtomicLong(1_790_000_000_000L);
    private final AtomicInteger calls = new AtomicInteger();
    private final AutomationModels.Definition definition = new AutomationModels.Definition();
    private AutomationModels.Result executionResult = AutomationModels.Result.success("completed");
    private TaskExecutionContext.CheckedAction action = () -> { };
    private TaskExecutionContext executionContext;

    public AutomationEngineTest(String backend) { this.backend = backend; }

    @Before
    public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        store = new AutomationStore(() -> new WebSite(db.dataSource()));
        authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        definition.type = "test.reminder";
        definition.titleKey = "reminder";
        definition.requiredActions = List.of("account.session");
        registry = new TaskRegistry(() -> List.of(new ScheduledTaskProvider() {
            public List<AutomationModels.Definition> definitions() { return List.of(definition); }
            public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) { return parameters; }
            public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
                calls.incrementAndGet();
                context.checkActive();
                executionContext = context;
                action.run();
                return executionResult;
            }
        }));
        service = new AutomationService(store, authority, registry, now::get);
    }

    @After
    public void close() throws Exception { if (db != null) db.close(); }

    private AutomationEngine engine() { return new AutomationEngine(store, authority, registry, now::get); }

    @Test
    public void recoveryPreservesCompletedStepsAndMarksAnUnfinishedWriteUncertainWithoutReplay() throws Exception {
        var completed = new AutomationModels.ChildResult(); completed.id = "read"; completed.status = "success";
        var write = new AutomationModels.ChildResult(); write.id = "save"; write.status = "running";
        executionResult.children = List.of(completed, write);
        action = () -> { executionContext.checkpoint(executionResult); throw new AssertionError("Simulated process loss"); };
        var task = service.save(actor, plan("interval"));
        assertThrows(AssertionError.class, () -> engine().runNow(actor, task.id, task.revision, 60000));
        String id = store.task(task.id).value.activeRun.id;
        assertEquals("running", store.run(id).value.status);
        assertEquals("success", store.run(id).value.result.children.get(0).status);
        now.addAndGet(60000);
        engine().tick("external", 60000);
        assertEquals("uncertain", store.run(id).value.status);
        assertEquals("success", store.run(id).value.result.children.get(0).status);
        assertEquals("uncertain", store.run(id).value.result.children.get(1).status);
        assertFalse(store.task(task.id).value.enabled);
        assertEquals(1, calls.get());
    }

    @Test
    public void thrownFailureRetainsTheLastDurableChildProgress() throws Exception {
        var step = new AutomationModels.ChildResult(); step.id = "save"; step.status = "success";
        step.actionPath = "/article-edit?id=42";
        executionResult.children = List.of(step);
        action = () -> { executionContext.checkpoint(executionResult); throw new IllegalStateException("Lost later response"); };
        var task = service.save(actor, plan("interval"));
        var run = engine().runNow(actor, task.id, task.revision, 60000);
        assertEquals("uncertain", run.status);
        assertEquals("/article-edit?id=42", store.run(run.id).value.result.children.get(0).actionPath);
        assertEquals("success", store.run(run.id).value.result.children.get(0).status);
    }

    @Test
    public void uncertainFailureLogsItsCauseAndRunIdentityWithoutExposingItInTheResult() throws Exception {
        var cause = new IllegalStateException("private execution diagnostic");
        var records = new java.util.ArrayList<java.util.logging.LogRecord>();
        var logger = java.util.logging.Logger.getLogger(AutomationEngine.class.getName());
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        };
        logger.addHandler(handler);
        try {
            action = () -> { throw cause; };
            var task = service.save(actor, plan("interval"));
            var run = engine().runNow(actor, task.id, task.revision, 60000);
            assertEquals("uncertain", run.status);
            assertEquals("automation.executionUncertain", run.errorCode);
            assertFalse(store.task(task.id).value.enabled);
            assertTrue(records.stream().anyMatch(record -> record.getThrown() == cause
                    && record.getMessage().contains(run.id) && record.getMessage().contains(task.type)));
            assertFalse(AutomationStore.JSON.toJson(run).contains(cause.getMessage()));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private AutomationModels.Plan plan(String kind) {
        AutomationModels.Plan plan = new AutomationModels.Plan();
        plan.type = definition.type;
        plan.name = "My reminder";
        plan.schedule = new AutomationModels.Schedule();
        plan.schedule.kind = kind;
        plan.schedule.at = now.get() + 1000;
        plan.schedule.intervalMinutes = 5;
        return plan;
    }

    @Test
    public void ordinaryTaskOwnsNestedStepsWithoutCreatingIndependentTasksOrRuns() throws Exception {
        var group = new AutomationModels.ChildResult();
        group.id = "prepare"; group.name = "Prepare writing plan"; group.status = "success";
        var step = new AutomationModels.ChildResult();
        step.id = "read-drafts"; step.name = "Read drafts"; step.status = "success"; step.text = "Reviewed three drafts.";
        group.children.add(step);
        var finalStep = new AutomationModels.ChildResult();
        finalStep.id = "save-outline"; finalStep.name = "Save outline"; finalStep.status = "success";
        executionResult.children = List.of(group, finalStep);
        var saved = service.save(actor, plan("interval"));
        var result = engine().runNow(actor, saved.id, saved.revision, 60000);
        assertEquals("success", result.status);
        assertEquals(1, store.taskCount(actor.getUserId()));
        assertEquals(1, store.runCount(actor.getUserId(), null));
        var view = AutomationDashboard.run(store.run(result.id).value);
        assertEquals(2, view.result.children.size());
        assertEquals("Prepare writing plan", view.result.children.get(0).name);
        assertEquals("Reviewed three drafts.", view.result.children.get(0).children.get(0).text);
        assertEquals("save-outline", view.result.children.get(1).id);
        assertEquals(1, calls.get());
    }

    @Test
    public void retentionPreservesActiveRunsAndOtherAccountsWhileLimitingFinishedHistory() throws Exception {
        long time = now.get();
        String taskId = AutomationStore.ownerPrefix(1) + java.util.UUID.randomUUID();
        for (int index = 0; index < 505; index++) {
            var run = new AutomationModels.Run();
            run.id = AutomationStore.ownerPrefix(1) + AutomationStore.timeKey(time + index) + "." + java.util.UUID.randomUUID();
            run.ownerId = 1;
            run.taskId = taskId;
            run.startedAt = time + index;
            run.finishedAt = time + index;
            run.status = "success";
            store.insertRun(run);
        }
        var active = new AutomationModels.Run();
        active.id = AutomationStore.ownerPrefix(1) + AutomationStore.timeKey(time - 1000) + "." + java.util.UUID.randomUUID();
        active.taskId = taskId;
        active.ownerId = 1;
        active.status = "running";
        store.insertRun(active);
        assertEquals(5, store.pruneRuns(1, now.get()));
        assertEquals(501, store.runCount(1, null));
        assertNotNull(store.run(active.id));
        assertEquals(0, store.pruneRuns(2, now.get() + java.time.Duration.ofDays(100).toMillis()));
        assertEquals(64, store.pruneRuns(1, now.get() + java.time.Duration.ofDays(100).toMillis()));
        assertNotNull(store.run(active.id));
    }

    @Test
    public void executesOneTimeTaskOnceAcrossSeparateTicksAndRetainsHistoryAfterDeletion() throws Exception {
        StoredTask task = service.save(actor, plan("once"));
        assertEquals(0, engine().tick("external", 60000).executed);
        now.addAndGet(1000);
        assertEquals(1, engine().tick("external", 60000).executed);
        assertEquals(0, engine().tick("internal", 60000).executed);
        assertEquals(1, calls.get());
        StoredTask completed = store.task(task.id).value;
        assertFalse(completed.enabled);
        assertEquals("success", completed.lastStatus);
        List<AutomationModels.Run> runs = service.runs(actor, task.id, 20, 0);
        assertEquals(1, runs.size());
        assertEquals("external", runs.get(0).trigger);
        assertEquals("My reminder", runs.get(0).taskName);
        service.delete(actor, task.id, completed.revision);
        assertEquals(1, service.runs(actor, task.id, 20, 0).size());
    }

    @Test
    public void repeatedTickDuringExecutionCannotClaimTheTaskAgain() throws Exception {
        StoredTask task = service.save(actor, plan("interval"));
        now.addAndGet(1000);
        action = () -> {
            assertEquals(0, engine().tick("external", 60000).executed);
            assertEquals("running", service.runs(actor, task.id, 20, 0).get(0).status);
        };
        assertEquals(1, engine().tick("internal", 60000).executed);
        assertEquals(1, calls.get());
        assertEquals(Long.valueOf(now.get() + 300000), store.task(task.id).value.nextRunAt);
    }

    @Test
    public void disablingWhileRunningDoesNotEraseOrCancelTheCurrentRun() throws Exception {
        StoredTask task = service.save(actor, plan("interval"));
        now.addAndGet(1000);
        action = () -> service.setEnabled(actor, task.id, task.revision, false);
        engine().tick("internal", 60000);
        StoredTask stopped = store.task(task.id).value;
        assertFalse(stopped.enabled);
        assertNull(stopped.activeRun);
        assertEquals("success", stopped.lastStatus);
        now.addAndGet(600000);
        assertEquals(0, engine().tick("external", 60000).executed);
    }

    @Test
    public void revokedIdentityStopsTheTaskBeforeItsBusinessAction() throws Exception {
        StoredTask task = service.save(actor, plan("once"));
        db.execute("UPDATE user SET enabled=? WHERE userId=?", false, 1);
        now.addAndGet(1000);
        assertEquals(1, engine().tick("external", 60000).needsAttention);
        assertEquals(0, calls.get());
        assertEquals("needs_attention", store.task(task.id).value.lastStatus);
        assertFalse(store.task(task.id).value.enabled);
        assertThrows(PermissionErrorException.class, () -> service.tasks(actor, 0));
    }

    @Test
    public void expiredRunBecomesUncertainAndCannotOverwriteRecoveryWithLateSuccess() throws Exception {
        StoredTask task = service.save(actor, plan("interval"));
        now.addAndGet(1000);
        action = () -> {
            now.addAndGet(40000);
            engine().tick("external", 60000);
        };
        assertThrows(IllegalStateException.class, () -> engine().tick("internal", 60000));
        assertEquals("uncertain", store.task(task.id).value.lastStatus);
        assertEquals("uncertain", service.runs(actor, task.id, 20, 0).get(0).status);
        assertEquals(0, engine().tick("internal", 60000).executed);
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> service.setEnabled(actor, task.id, task.revision, true));
    }

    @Test
    public void unknownOutcomeDoesNotAutomaticallyRepeatSideEffects() throws Exception {
        StoredTask task = service.save(actor, plan("interval"));
        action = () -> { throw new IllegalStateException("An external action may have completed"); };
        now.addAndGet(1000);
        assertEquals(1, engine().tick("external", 60000).failed);
        now.addAndGet(300000);
        assertEquals(0, engine().tick("external", 60000).executed);
        AutomationModels.Run run = service.runs(actor, task.id, 20, 0).get(0);
        assertEquals("automation.executionUncertain", run.errorCode);
        assertEquals("uncertain", run.status);
        assertEquals(1, calls.get());
    }

    @Test
    public void reviewingAndSavingAnUncertainPlanAllowsManualExecutionAgain() throws Exception {
        StoredTask task = service.save(actor, plan("interval"));
        action = () -> {throw new IllegalStateException("unknown outcome");};
        now.addAndGet(1000);
        engine().tick("external", 60000);
        assertThrows(IllegalStateException.class, () -> engine().runNow(actor, task.id, task.revision, 60000));
        var reviewed = plan("interval");
        reviewed.id = task.id;
        reviewed.revision = task.revision;
        StoredTask saved = service.save(actor, reviewed);
        action = () -> {};
        assertEquals("success", engine().runNow(actor, task.id, saved.revision, 60000).status);
        assertEquals(2, store.runCount(actor.getUserId(), task.id));
    }

    @Test
    public void editsUseRevisionChecksAndServerDerivedAccountPermissions() throws Exception {
        definition.requiredActions = List.of("system.manage");
        AutomationModels.Plan plan = plan("once");
        var restricted = actor.restrictActions(List.of("account.session"));
        assertThrows(PermissionErrorException.class, () -> service.save(restricted, plan));
        StoredTask task = service.save(actor, plan);
        assertEquals(List.of("system.manage"), task.executionActions);
        AutomationModels.Plan update = plan("once");
        update.id = task.id; update.revision = task.revision;
        service.save(actor, update);
        assertThrows(IllegalStateException.class, () -> service.save(actor, update));
    }

    @Test
    public void insufficientRequestBudgetLeavesDueTasksAvailableForALaterTick() throws Exception {
        StoredTask task = service.save(actor, plan("once"));
        now.addAndGet(1000);
        AutomationModels.TickResult tick = engine().tick("external", 1000);
        assertTrue(tick.budgetExhausted);
        assertEquals(List.of(task.id), tick.insufficientBudgetTaskIds);
        assertEquals(definition.timeoutSeconds + 5, tick.requiredBudgetSeconds);
        assertEquals(0, tick.executed);
        assertNull(store.task(task.id).value.activeRun);
        assertEquals(1, engine().tick("external", 60000).executed);
    }

    @Test
    public void permissionRevocationAtTheWebApiClaimWriteCannotStartExecution() throws Exception {
        if (!"web-api".equals(backend)) return;
        StoredTask task = service.save(actor, plan("once"));
        AtomicBoolean changed = new AtomicBoolean();
        db.beforeWebApiUpdate((sql, args) -> {
            if (sql.startsWith("UPDATE website SET value=?,remark=?") && changed.compareAndSet(false, true)) {
                db.execute("UPDATE user SET enabled=? WHERE userId=?", false, 1);
            }
        });
        now.addAndGet(1000);
        assertEquals(0, engine().tick("external", 60000).executed);
        assertEquals(0, calls.get());
        assertNull(store.task(task.id).value.activeRun);
        assertEquals(1, engine().tick("external", 60000).needsAttention);
    }
}
