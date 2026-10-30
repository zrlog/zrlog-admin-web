package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SystemTaskPermissionsTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() { return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"}); }
    private final String backend;
    public SystemTaskPermissionsTest(String backend) { this.backend = backend; }
    private InMemoryZrLogDatabase db;
    private AutomationStore store;
    private TaskAuthority authority;
    private AutomationService service;
    private AutomationEngine engine;
    private AutomationDefaults defaults;
    private final AtomicLong now = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger effects = new AtomicInteger();
    private final AutomationModels.Definition definition = new AutomationModels.Definition();

    @Before public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
        db.execute("INSERT INTO user(userId,userName,role,enabled,authVersion) VALUES(?,?,?,?,?)", 2, "second-admin", "admin", true, 0);
        db.execute("INSERT INTO user(userId,userName,role,enabled,authVersion) VALUES(?,?,?,?,?)", 3, "writer", "author", true, 0);
        store = new AutomationStore(() -> new WebSite(db.dataSource()));
        authority = new TaskAuthority(new AccountIdentityService(() -> new User(db.dataSource())), () -> new User(db.dataSource()));
        definition.type = "system.test-maintenance"; definition.titleKey = "admin.automation.cache.title";
        definition.systemTask = true; definition.enabledByDefault = true;
        definition.requiredActions = List.of("system.manage");
        definition.defaultSchedule = new AutomationModels.Schedule(); definition.defaultSchedule.kind = "interval";
        definition.defaultSchedule.intervalMinutes = 1;
        ScheduledTaskProvider provider = new ScheduledTaskProvider() {
            public List<AutomationModels.Definition> definitions() { return List.of(definition); }
            public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
                AccountPermissionService.requireActions(actor, definition.requiredActions); return parameters;
            }
            public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
                context.checkActive(); AccountPermissionService.requireActions(context.actor(), definition.requiredActions);
                effects.incrementAndGet();
                var result = AutomationModels.Result.success("admin.automation.cache.completed"); result.notifyOwner = true; return result;
            }
        };
        var registry = new TaskRegistry(() -> List.of(provider, new ReminderTaskProvider()));
        service = new AutomationService(store, authority, registry, now::get);
        engine = new AutomationEngine(store, authority, registry, now::get);
        defaults = new AutomationDefaults(store, () -> new User(db.dataSource()), new WebsiteKvService(() -> new WebSite(db.dataSource())), registry);
        assertTrue(defaults.initialize());
    }
    @After public void close() throws Exception { if (db != null) db.close(); }
    private AccountAccess actor(int id) throws Exception { return AccountAccess.from(new User(db.dataSource()).loadById(id)); }
    private StoredTask task() throws Exception { return service.systemTasks(actor(1)).get(0); }
    private AutomationModels.Plan plan(StoredTask task) { return AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(task), AutomationModels.Plan.class); }

    @Test public void adminsShareOneConfigurationWithRevisionChecksAndNoExtraGrant() throws Exception {
        var task = task(); var admin = actor(2);
        assertEquals(task.id, service.systemTasks(admin).get(0).id);
        assertEquals(List.of("system.manage"), service.definitions(admin).get(0).requiredActions);
        var change = plan(task); change.schedule.intervalMinutes = 12;
        var saved = service.save(admin, change);
        assertEquals(0, saved.ownerId); assertEquals(Integer.valueOf(2), saved.updatedBy);
        assertEquals(List.of("system.manage"), saved.executionActions);
        assertEquals(Integer.valueOf(12), task().schedule.intervalMinutes);
        assertThrows(IllegalStateException.class, () -> service.setEnabled(actor(1), task.id, task.revision, false));
        var stopped = service.setEnabled(admin, saved.id, saved.revision, false);
        assertFalse(task().enabled); assertTrue(defaults.initialize()); assertFalse(task().enabled);
        assertThrows(IllegalArgumentException.class, () -> service.delete(admin, stopped.id, stopped.revision));
        var duplicate = plan(stopped); duplicate.id = null;
        assertThrows(IllegalArgumentException.class, () -> service.save(admin, duplicate));
        assertEquals(1, store.taskCount(0));
    }

    @Test public void insufficientCurrentPermissionsCannotSeeChangeOrExecuteSystemTasks() throws Exception {
        var task = task(); var writer = actor(3);
        assertTrue(service.systemTasks(writer).isEmpty());
        assertTrue(service.definitions(writer).stream().noneMatch(item -> item.systemTask));
        assertThrows(PermissionErrorException.class, () -> service.save(writer, plan(task)));
        assertThrows(PermissionErrorException.class, () -> service.setEnabled(writer, task.id, task.revision, false));
        assertThrows(PermissionErrorException.class, () -> engine.runNow(writer, task.id, task.revision, 60000));
        var restricted = actor(2).restrictActions(List.of("account.session"));
        assertThrows(PermissionErrorException.class, () -> engine.runNow(restricted, task.id, task.revision, 60000));
        assertEquals(0, effects.get()); assertEquals(0, store.runCount(0, null));
    }

    @Test public void manualRunsUseTheCurrentOperatorAndShareResultsButNotReadState() throws Exception {
        var task = task(); var admin = actor(2); var owner = actor(1);
        var run = engine.runNow(admin, task.id, task.revision, 60000);
        assertEquals(0, run.ownerId); assertEquals(Integer.valueOf(2), run.triggeredBy);
        assertEquals(Integer.valueOf(2), run.executionUserId); assertEquals(1, effects.get());
        assertEquals(run.id, service.runs(owner, null, 20, 0).get(0).id);
        assertEquals(run.id, service.runs(admin, null, 20, 0).get(0).id);
        assertEquals(1, service.runCount(admin, null));
        assertThrows(PermissionErrorException.class, () -> service.run(actor(3), run.id));
        assertTrue(service.runs(actor(3), null, 20, 0).isEmpty());
        var notices = new AutomationNotificationService(store, authority, service);
        assertEquals(1, notices.list(owner).size()); assertEquals(1, notices.list(admin).size());
        assertTrue(notices.markRead(admin, AutomationNotificationService.PREFIX + run.id));
        assertTrue(notices.list(admin).isEmpty()); assertEquals(1, notices.list(owner).size());
        db.execute("UPDATE user SET role=? WHERE userId=?", "author", 2);
        assertTrue(service.runs(actor(2), null, 20, 0).isEmpty());
        assertEquals(0, service.runCount(actor(2), null));
        assertThrows(PermissionErrorException.class, () -> service.run(actor(2), run.id));
        assertTrue(notices.list(actor(2)).isEmpty());
        assertThrows(PermissionErrorException.class, () -> notices.markRead(actor(2), AutomationNotificationService.PREFIX + run.id));
        assertEquals(1, store.pruneRuns(0, now.get() + java.time.Duration.ofDays(91).toMillis()));
        assertNull(store.read(AutomationStore.noticeReadKey(2, run.id), Long.class));
    }

    @Test public void siteExecutionRechecksTheActualCurrentOwnerAfterOwnershipChanges() throws Exception {
        var task = task();
        db.execute("UPDATE user SET role=?,authVersion=? WHERE userId=?", "admin", 3, 1);
        db.execute("UPDATE user SET role=?,authVersion=? WHERE userId=?", "owner", 2, 2);
        now.set(task.nextRunAt);
        var tick = engine.tick("internal", 60000);
        assertEquals(1, tick.executed); assertEquals(1, effects.get());
        var run = store.run(tick.runIds.get(0)).value;
        assertEquals(0, run.ownerId); assertNull(run.triggeredBy); assertEquals(Integer.valueOf(2), run.executionUserId);
        db.execute("UPDATE user SET enabled=? WHERE userId=?", false, 2);
        now.addAndGet(60000);
        assertEquals(1, engine.tick("internal", 60000).needsAttention);
        assertEquals(1, effects.get()); assertFalse(store.task(task.id).value.enabled);
    }

    @Test public void personalTasksRemainPrivateAndCallerSuppliedGrantsCannotBroadenPermissions() throws Exception {
        var writer = actor(3);
        var input = new AutomationModels.Plan(); input.name = "Private reminder"; input.type = "assistant.reminder";
        input.parameters.addProperty("text", "My writing plan"); input.schedule = new AutomationModels.Schedule();
        input.schedule.kind = "interval"; input.schedule.intervalMinutes = 5; input.schedule.at = now.get() + 1000;
        var forged = AutomationStore.JSON.toJsonTree(input).getAsJsonObject();
        forged.add("executionActions", AutomationStore.JSON.toJsonTree(List.of("system.manage")));
        var saved = service.save(writer, AutomationStore.JSON.fromJson(forged, AutomationModels.Plan.class));
        assertEquals(3, saved.ownerId); assertEquals(List.of("account.session"), saved.executionActions);
        assertThrows(PermissionErrorException.class, () -> service.setEnabled(actor(2), saved.id, saved.revision, false));
        var run = engine.runNow(writer, saved.id, saved.revision, 60000);
        assertThrows(PermissionErrorException.class, () -> service.run(actor(2), run.id));
        assertTrue(service.runs(actor(2), null, 20, 0).isEmpty());
    }
}
