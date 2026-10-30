package com.zrlog.admin.business.automation;

import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AutomationDefaultsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }
    private final String backend;
    public AutomationDefaultsTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase open() throws Exception {
        InMemoryZrLogDatabase db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        return db;
    }

    @Test
    public void keepsExistingEnablementAndDoesNotRecreateDeletedDefaultsAfterRestart() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("DELETE FROM website WHERE name IN (?,?)", "autoUpgradeVersion", "cache_timeout_minutes");
            db.execute("INSERT INTO website(name,value) VALUES(?,?)", "autoUpgradeVersion", "-1");
            db.execute("INSERT INTO website(name,value) VALUES(?,?)", "cache_timeout_minutes", "7");
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())));
            assertTrue(defaults.initialize());
            assertEquals(2, store.taskCount(1));
            var tasks = store.tasks(1, 100, 0);
            var cache = tasks.stream().filter(row -> row.value.type.equals("site.refresh-cache")).findFirst().orElseThrow();
            var version = tasks.stream().filter(row -> row.value.type.equals("system.check-version")).findFirst().orElseThrow();
            assertTrue(cache.value.enabled);
            assertEquals(Integer.valueOf(7), cache.value.schedule.intervalMinutes);
            assertFalse(version.value.enabled);
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(store.deleteTask(cache, actor));
            assertTrue(new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource()))).initialize());
            assertEquals(1, store.taskCount(1));
            assertNull(store.task(cache.value.id));
        }
    }

    @Test
    public void completedMigrationFencesAStaleInitializerAfterDeletion() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var state = new AutomationDefaults.State();
            state.ownerId = 1;
            state.createdAt = System.currentTimeMillis();
            state.cacheMinutes = 5;
            state.checkSeconds = 86400;
            assertTrue(store.write(AutomationDefaults.KEY, null, state));
            var stale = store.read(AutomationDefaults.KEY, AutomationDefaults.State.class);
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())));
            assertTrue(defaults.initialize());
            var cache = store.tasks(1, 100, 0).get(0);
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(store.deleteTask(cache, actor));
            store.insertDefaultTask(cache.value, actor, stale);
            assertNull(store.task(cache.value.id));
        }
    }

    @Test
    public void migratesOnePluginParentAndNeverRecreatesItAfterDeletion() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())));
            assertTrue(defaults.initialize());
            assertTrue(defaults.initializePluginTrigger());
            assertEquals(3, store.taskCount(1));
            var parent = store.tasks(1, 100, 0).stream().filter(row -> row.value.type.equals(PluginScheduledTaskProvider.TYPE)).findFirst().orElseThrow();
            assertTrue(parent.value.enabled);
            assertEquals(Integer.valueOf(1), parent.value.schedule.intervalMinutes);
            assertEquals(java.util.List.of("plugin.manage"), parent.value.authorizedActions);
            assertTrue(parent.value.parameters.entrySet().isEmpty());
            AccountAccess owner = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(store.deleteTask(parent, owner));
            assertTrue(defaults.initializePluginTrigger());
            assertNull(store.task(parent.value.id));
            assertEquals(2, store.taskCount(1));
        }
    }

    @Test
    public void pluginMigrationCompletionFencesStaleInsertionAfterDeletion() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var state = new AutomationDefaults.State();
            state.ownerId = 1; state.createdAt = System.currentTimeMillis();
            assertTrue(store.write(AutomationDefaults.PLUGIN_KEY, null, state));
            var stale = store.read(AutomationDefaults.PLUGIN_KEY, AutomationDefaults.State.class);
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())));
            assertTrue(defaults.initializePluginTrigger());
            var parent = store.tasks(1, 100, 0).get(0);
            AccountAccess owner = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(store.deleteTask(parent, owner));
            store.insertDefaultTask(parent.value, owner, stale);
            assertNull(store.task(parent.value.id));
        }
    }

    @Test
    public void doesNotAssignMaintenancePermissionsToAnEditor() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("UPDATE user SET role=? WHERE userId=?", "editor", 1);
            var store = new AutomationStore(() -> new WebSite(db.dataSource()));
            var defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())));
            assertFalse(defaults.initialize());
            assertFalse(defaults.initializePluginTrigger());
            assertEquals(0, store.taskCount(1));
            assertNull(store.read(AutomationDefaults.KEY, AutomationDefaults.State.class));
        }
    }
}
