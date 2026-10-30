package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.PluginSchedulerTickResult;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class PluginScheduledTaskTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> backends() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }
    private final String backend;
    public PluginScheduledTaskTest(String backend) { this.backend = backend; }

    @Test
    public void singleSharedParentStoresNestedResultsWithoutImportingChildPlans() throws Exception {
        verify("success", "success", false);
    }

    @Test
    public void unknownChildOutcomePausesParentAndPreservesOtherChildResults() throws Exception {
        verify("uncertain", "needs_attention", true);
    }

    @Test
    public void failedChildDoesNotMakeParentSuccessful() throws Exception {
        verify("error", "failed", false);
    }

    @Test
    public void lostResponseKeepsParentCorrelationAndDoesNotInventChildSuccess() throws Exception {
        verify(null, "uncertain", true);
    }

    private void verify(String childStatus, String expectedParent, boolean paused) throws Exception {
        try (var db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            AtomicInteger invocations = new AtomicInteger();
            PluginCorePlugin core = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginCorePlugin.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("tickScheduledTasks")) throw new AssertionError("Unexpected plugin operation: " + method.getName());
                        invocations.incrementAndGet();
                        assertEquals(actor.getUserId(), args[1]);
                        assertTrue((Long) args[2] > 600000);
                        if (childStatus == null) throw new java.io.IOException("connection lost");
                        PluginSchedulerTickResult tick = new PluginSchedulerTickResult();
                        tick.triggerId = (String) args[0];
                        var success = child("first", "success");
                        var other = child("second", childStatus);
                        tick.children = List.of(success, other);
                        tick.executedCount = childStatus.equals("success") ? 2 : 1;
                        tick.failedCount = childStatus.equals("success") ? 0 : 1;
                        tick.uncertainCount = childStatus.equals("uncertain") ? 1 : 0;
                        return tick;
                    });
            var provider = new PluginScheduledTaskProvider(() -> core);
            var registry = new TaskRegistry(() -> List.of(provider));
            var service = new AutomationService(store, authority, registry, System::currentTimeMillis);
            var engine = new AutomationEngine(store, authority, registry, System::currentTimeMillis);
            assertEquals(1, service.definitions(actor).size());
            var restricted = actor.restrictActions(List.of(AccountAction.SESSION.getId()));
            assertTrue(service.definitions(restricted).isEmpty());
            var plan = new AutomationModels.Plan();
            plan.name = "Plugin schedules"; plan.type = PluginScheduledTaskProvider.TYPE;
            plan.schedule = provider.definitions().get(0).defaultSchedule;
            plan.schedule.at = System.currentTimeMillis() + 60000;
            assertEquals("automation.systemTaskManaged", assertThrows(IllegalArgumentException.class, () -> service.save(actor, plan)).getMessage());
            db.execute("UPDATE user SET role=? WHERE userId=?", "owner", actor.getUserId());
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()),
                    new com.zrlog.business.service.WebsiteKvService(() -> new WebSite(db.dataSource())), registry);
            assertTrue(defaults.initializePluginTrigger());
            var saved = store.tasks(0, 100, 0).get(0).value;
            assertTrue(saved.enabled);
            assertEquals(List.of(AccountAction.PLUGIN_MANAGE.getId()), saved.executionActions);
            var owner = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var run = engine.runNow(owner, saved.id, saved.revision, 660000);
            assertEquals(expectedParent, run.status);
            assertEquals(run.id, run.result.externalRunId);
            assertEquals(childStatus == null ? 0 : 2, run.result.children.size());
            assertEquals("/plugin?page=runtime-scheduler", run.result.actionPath);
            if (childStatus != null) {
                assertEquals("first-run", run.result.children.get(0).externalRunId);
                assertEquals("success", run.result.children.get(0).status);
                assertEquals("error".equals(childStatus) ? "failed" : childStatus, run.result.children.get(1).status);
            }
            assertEquals(1, store.taskCount(0));
            assertEquals(1, store.runCount(0, null));
            var persisted = store.run(run.id).value;
            assertEquals(childStatus == null ? 0 : 2, persisted.result.children.size());
            if (childStatus != null) assertEquals("Plugin first", AutomationDashboard.run(persisted).result.children.get(0).name);
            assertEquals(!paused, store.task(saved.id).value.enabled);
            assertEquals(1, invocations.get());
        }
    }

    private PluginSchedulerTickResult.Child child(String id, String status) {
        var child = new PluginSchedulerTickResult.Child();
        child.automationId = id; child.name = "Plugin " + id; child.runId = id + "-run"; child.status = status;
        child.startedAt = System.currentTimeMillis(); child.finishedAt = child.startedAt + 1;
        return child;
    }
}
