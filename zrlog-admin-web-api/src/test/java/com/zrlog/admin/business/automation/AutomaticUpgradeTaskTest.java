package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AutomaticUpgradeTaskTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }
    private final String backend;
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private AutomaticUpgradeTaskProvider provider;
    private AutomationService service;
    private AutomationEngine engine;
    private AccountAccess actor;
    private final AtomicLong now = new AtomicLong(1_790_000_000_000L);
    private final FakeOperations operations = new FakeOperations();

    public AutomaticUpgradeTaskTest(String backend) { this.backend = backend; }

    @Before
    public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        store = new AutomationStore(() -> new WebSite(db.dataSource()));
        var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        provider = new AutomaticUpgradeTaskProvider(store, operations, now::get);
        var registry = new TaskRegistry(() -> List.of(provider));
        service = new AutomationService(store, authority, registry, now::get);
        engine = new AutomationEngine(store, authority, registry, now::get);
        new AutomationDefaults(store, () -> new User(db.dataSource()),
                new com.zrlog.business.service.WebsiteKvService(() -> new WebSite(db.dataSource())), registry).initialize();
    }

    @After
    public void close() throws Exception { if (db != null) db.close(); }

    private AutomationModels.Plan plan() throws Exception {
        var task = store.task(AutomationDefaults.systemId("system.auto-upgrade")).value;
        var plan = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(task), AutomationModels.Plan.class);
        plan.enabled = true;
        plan.parameters.addProperty("channel", operations.channel);
        plan.schedule = new AutomationModels.Schedule();
        plan.schedule.kind = "interval"; plan.schedule.intervalMinutes = 60;
        plan.schedule.at = now.get() + 1000;
        return plan;
    }

    @Test
    public void usesAccountPermissionWithoutAnExtraGrantAndRejectsUnsupportedRuntime() throws Exception {
        var plan = plan();
        var restricted = actor.restrictActions(List.of("account.session"));
        assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class, () -> service.save(restricted, plan));
        assertEquals(List.of("system.manage"), service.save(actor, plan).executionActions);
        operations.available = false;
        assertFalse(provider.definitions().get(0).available);
        assertThrows(IllegalArgumentException.class, () -> service.save(actor, plan()));
        assertNull(store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class));
    }

    @Test
    public void externalTriggerRequiresEnoughBudgetAndConfirmsOnlyTheInstalledBuild() throws Exception {
        var task = service.save(actor, plan());
        now.addAndGet(1000);
        var shortTick = engine.tick("external", 60000);
        assertEquals(0, shortTick.executed);
        assertEquals(0, operations.submits);
        assertNull(store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class));
        assertEquals(1, engine.tick("external", 660000).executed);
        assertEquals("submitted", service.runs(actor, task.id, 20, 0).get(0).status);
        provider.confirmInstalled();
        assertEquals("submitted", service.runs(actor, task.id, 20, 0).get(0).status);
        operations.currentBuild = "new-build";
        provider.confirmInstalled();
        assertEquals("success", service.runs(actor, task.id, 20, 0).get(0).status);
        assertEquals(1, operations.submits);
    }

    @Test
    public void storesTheExactBuildBeforeSubmittingAndDoesNotSubmitTwice() throws Exception {
        var first = service.save(actor, plan());
        now.addAndGet(1000);
        operations.beforeSubmit = () -> {
            var receipt = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class).value;
            assertEquals("new-build", receipt.buildId);
            assertEquals("running", store.run(receipt.runId).value.status);
        };
        assertEquals(1, engine.tick("internal", 660000).executed);
        assertEquals(1, operations.submits);
        assertEquals("submitted", store.task(first.id).value.lastStatus);
        now.addAndGet(3600000);
        engine.tick("internal", 660000);
        assertEquals(1, operations.submits);
        assertEquals(1, operations.checks);
    }

    @Test
    public void confirmsSubmissionOnlyAfterTheTargetBuildStarts() throws Exception {
        var plan = plan();
        var task = service.save(actor, plan);
        now.addAndGet(1000);
        engine.tick("internal", 660000);
        provider.confirmInstalled();
        assertEquals("submitted", service.runs(actor, task.id, 20, 0).get(0).status);
        operations.currentBuild = "new-build";
        provider.confirmInstalled();
        assertEquals("success", service.runs(actor, task.id, 20, 0).get(0).status);
        assertEquals("success", store.task(task.id).value.lastStatus);
        assertTrue(store.task(task.id).value.enabled);
        assertEquals(1, operations.submits);
    }

    @Test
    public void upgradeNotificationAppearsOnlyAfterBuildConfirmationAndDoesNotRepeat() throws Exception {
        now.set(System.currentTimeMillis());
        var task = service.save(actor, plan());
        var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        var inbox = new AutomationNotificationService(store, authority, service);
        now.addAndGet(1000);
        engine.tick("internal", 660000);
        assertTrue(inbox.list(actor).isEmpty());
        var submitted = service.runs(actor, task.id, 20, 0).get(0);
        operations.currentBuild = "new-build";
        now.addAndGet(2000);
        provider.confirmInstalled();
        var notices = inbox.list(actor);
        assertEquals(1, notices.size());
        assertEquals(AutomationNotificationService.PREFIX + submitted.id, notices.get(0).getTaskKey());
        assertEquals(Long.valueOf(now.get()), store.run(submitted.id).value.finishedAt);
        assertTrue(inbox.markRead(actor, notices.get(0).getTaskKey()));
        provider.confirmInstalled();
        now.addAndGet(3600000);
        engine.tick("internal", 660000);
        assertTrue(inbox.list(actor).isEmpty());
        assertTrue(inbox.list(actor).isEmpty());
        assertEquals(1, operations.submits);
    }

    @Test
    public void interruptedProcessCanRecoverFromInstalledBuildProofWithoutRepeatingUpgrade() throws Exception {
        var task = service.save(actor, plan());
        now.addAndGet(1000);
        operations.beforeSubmit = () -> { throw new SimulatedRestart(); };
        assertThrows(SimulatedRestart.class, () -> engine.tick("internal", 660000));
        assertEquals("running", service.runs(actor, task.id, 20, 0).get(0).status);
        operations.currentBuild = "new-build";
        now.addAndGet(660000);
        engine.tick("internal", 660000);
        assertEquals("success", service.runs(actor, task.id, 20, 0).get(0).status);
        assertTrue(store.task(task.id).value.enabled);
        assertEquals(1, operations.submits);
    }

    @Test
    public void unknownSubmissionRequiresReviewBeforeRetryingTheSharedPlan() throws Exception {
        var task = service.save(actor, plan());
        now.addAndGet(1000);
        operations.beforeSubmit = () -> { throw new IllegalStateException("connection lost after submission"); };
        engine.tick("internal", 660000);
        assertEquals("uncertain", store.task(task.id).value.lastStatus);
        now.addAndGet(3600000);
        assertEquals(0, engine.tick("internal", 660000).executed);
        var edited = plan();
        edited.id = task.id;
        edited.revision = task.revision;
        edited.parameters.addProperty("retryUnconfirmed", true);
        var reviewed = service.save(actor, edited);
        operations.beforeSubmit = () -> { };
        assertEquals("submitted", engine.runNow(actor, reviewed.id, reviewed.revision, 660000).status);
        assertEquals(2, operations.submits);
    }

    @Test
    public void aStalledAcceptedUpgradeRequiresAReviewedRetryAfterTheConfirmationWindow() throws Exception {
        var task = service.save(actor, plan());
        now.addAndGet(1000);
        engine.tick("internal", 660000);
        var retry = new JsonObject();
        retry.addProperty("channel", operations.channel);
        retry.addProperty("retryUnconfirmed", true);
        assertThrows(IllegalArgumentException.class, () -> provider.prepare(task.type, retry, actor));
        now.addAndGet(3600000);
        engine.tick("internal", 660000);
        assertEquals("needs_attention", store.task(task.id).value.lastStatus);
        var reviewed = plan();
        reviewed.id = task.id;
        reviewed.revision = task.revision;
        reviewed.parameters = retry;
        var saved = service.save(actor, reviewed);
        assertEquals("submitted", engine.runNow(actor, saved.id, saved.revision, 660000).status);
        assertEquals(2, operations.submits);
    }

    @Test
    public void changingTheChannelRequiresReviewAndNetworkFailureDoesNotDisableFutureChecks() throws Exception {
        var task = service.save(actor, plan());
        operations.channel = "preview";
        now.addAndGet(1000);
        assertEquals(1, engine.tick("internal", 660000).needsAttention);
        assertEquals(0, operations.submits);
        assertNull(store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class));
        var fresh = service.save(actor, plan());
        operations.failCheck = true;
        now.addAndGet(1000);
        engine.tick("internal", 660000);
        assertEquals("failed", store.task(fresh.id).value.lastStatus);
        assertTrue(store.task(fresh.id).value.enabled);
        assertEquals(task.id, fresh.id);
    }

    @Test
    public void anotherAdministratorCanReviewASharedFailedUpgradeWithoutReplayingIt() throws Exception {
        var task = service.save(actor, plan());
        operations.beforeSubmit = () -> { throw new IllegalStateException("unknown submission outcome"); };
        now.addAndGet(1000); engine.tick("internal", 660000);
        db.execute("INSERT INTO user(userId,userName,role,enabled,authVersion) VALUES(?,?,?,?,?)", 2, "admin-two", "admin", true, 0);
        var admin = AccountAccess.from(new User(db.dataSource()).loadById(2));
        assertThrows(IllegalStateException.class, () -> service.setEnabled(admin, task.id, task.revision, true));
        var review = plan(); review.parameters.addProperty("retryUnconfirmed", true);
        var saved = service.save(admin, review);
        assertEquals(1, operations.submits);
        operations.beforeSubmit = () -> { };
        assertEquals("submitted", engine.runNow(admin, saved.id, saved.revision, 660000).status);
        assertEquals(2, operations.submits);
    }

    private static class SimulatedRestart extends Error { }

    private static class FakeOperations implements AutomaticUpgradeTaskProvider.Operations {
        boolean available = true;
        boolean failCheck;
        String currentBuild = "old-build";
        String channel = "release";
        int checks;
        int submits;
        TaskExecutionContext.CheckedAction beforeSubmit = () -> { };
        public boolean available() { return available; }
        public String channel() { return channel; }
        public String currentBuildId() { return currentBuild; }
        public PreCheckVersionResponse check() {
            checks++;
            if (failCheck) throw new IllegalStateException("offline");
            var result = new PreCheckVersionResponse();
            result.setOnlineUpgradable(true);
            result.setUpgrade(true);
            var version = new Version();
            version.setBuildId("new-build");
            version.setVersion("4.2.0");
            result.setVersion(version);
            return result;
        }
        public boolean submit(Version target, TaskExecutionContext context) throws Exception {
            submits++;
            beforeSubmit.run();
            return true;
        }
    }
}
