package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.service.HostMaintenanceTaskProvider;
import com.zrlog.admin.business.service.TemporaryCleanupTaskProvider;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AutomationDefaultsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() { return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"}); }
    private final String backend;
    public AutomationDefaultsTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase open() throws Exception {
        var db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        return db;
    }
    private AutomationDefaults defaults(InMemoryZrLogDatabase db, AutomationStore store) {
        var upgrade = new AutomaticUpgradeTaskProvider(store, new AutomaticUpgradeTaskProvider.Operations() {
            public boolean available() { return true; }
            public String channel() { return "release"; }
            public String currentBuildId() { return "old"; }
            public PreCheckVersionResponse check() { throw new AssertionError("Initialization must not check or upgrade"); }
            public void synchronizeResources(TaskExecutionContext context) { throw new AssertionError("Initialization must not synchronize resources"); }
            public boolean submit(Version target, TaskExecutionContext context) { throw new AssertionError("Initialization must not upgrade"); }
        }, System::currentTimeMillis);
        var registry = new TaskRegistry(() -> List.of(new HostMaintenanceTaskProvider(null, null, () -> null),
                new TemporaryCleanupTaskProvider(null, null, null), upgrade, new PluginScheduledTaskProvider(() -> null)));
        return new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())), registry);
    }

    @Test
    public void maintenanceIsSiteWideAndEnabledExceptAutomaticUpgrade() throws Exception {
        try (var db = open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            assertTrue(defaults(db, store).initialize()); assertTrue(defaults(db, store).initializePluginTrigger());
            assertEquals(5, store.taskCount(0)); assertEquals(0, store.taskCount(1));
            for (var row : store.tasks(0, 100, 0)) {
                assertEquals(!"system.auto-upgrade".equals(row.value.type), row.value.enabled);
                assertEquals(0, row.value.ownerId);
                assertTrue(row.value.nextRunAt > 0);
            }
            var cleanup = store.task(AutomationDefaults.systemId("system.cleanup-temporary")).value;
            assertEquals(7, cleanup.parameters.get("retentionDays").getAsInt());
            assertEquals(Integer.valueOf(1440), cleanup.schedule.intervalMinutes);
            assertEquals("release", store.task(AutomationDefaults.systemId("system.auto-upgrade")).value.parameters.get("channel").getAsString());
            assertTrue(defaults(db, store).initialize()); assertTrue(defaults(db, store).initializePluginTrigger());
            assertEquals(5, store.taskCount(0));
        }
    }

    @Test
    public void repeatedInitializationPreservesUserSettingsAndIgnoresOldDevelopmentStorage() throws Exception {
        try (var db = open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            db.execute("INSERT INTO website(name,value,remark) VALUES(?,?,?)",
                    "assistant.task:old-development-task", "invalid old format", "0");
            assertTrue(defaults(db, store).initialize());
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var row = store.task(AutomationDefaults.systemId("system.cleanup-temporary"));
            var stopped = row.copy(StoredTask.class);
            stopped.enabled = false; stopped.schedule.intervalMinutes = 120;
            stopped.parameters.addProperty("retentionDays", 31); stopped.revision++;
            assertTrue(store.replaceTask(row, stopped, actor));
            // Simulate a restart between inserting defaults and completing initialization.
            db.execute("DELETE FROM website WHERE name=?", AutomationDefaults.KEY);
            assertTrue(defaults(db, store).initialize());
            var current = store.task(stopped.id).value;
            assertFalse(current.enabled); assertEquals(stopped.revision, current.revision);
            assertEquals(Integer.valueOf(120), current.schedule.intervalMinutes);
            assertEquals(31, current.parameters.get("retentionDays").getAsInt());
            assertEquals(4, store.taskCount(0)); assertEquals(0, store.due(0, 64).size());
        }
    }

    @Test
    public void initializationCannotInventAnOwnerOrBypassAccountPermissions() throws Exception {
        try (var db = open()) {
            db.execute("UPDATE user SET role=? WHERE userId=?", "editor", 1);
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            assertFalse(defaults(db, store).initialize()); assertFalse(defaults(db, store).initializePluginTrigger());
            assertEquals(0, store.taskCount(0));
        }
    }
}
