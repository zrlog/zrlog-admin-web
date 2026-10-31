package com.zrlog.admin.business.automation;

import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** One site-wide maintenance configuration per provider, created once without replacing user settings. */
@Singleton
public final class AutomationDefaults {
    static final String KEY = "assistant.v1.defaults";
    static final String PLUGIN_KEY = "assistant.v1.plugin-defaults";
    private final AutomationStore store;
    private final Provider<User> users;
    private final WebsiteKvService settings;
    private final TaskRegistry registry;

    @Inject
    public AutomationDefaults(AutomationStore store, Provider<User> users, WebsiteKvService settings, TaskRegistry registry) {
        this.store = store; this.users = users; this.settings = settings; this.registry = registry;
    }

    public boolean initialize() throws SQLException { return initialize(KEY, false); }
    public boolean initializePluginTrigger() throws SQLException { return initialize(PLUGIN_KEY, true); }

    private boolean initialize(String key, boolean plugins) throws SQLException {
        var row = store.read(key, State.class);
        if (row != null && row.value.complete) return true;
        AccountAccess actor = AccountAccess.from(users.get().queryFirstWithParams(
                "SELECT userId,role,enabled,authVersion FROM user WHERE role=? AND enabled=? ORDER BY userId LIMIT 1", "owner", true));
        if (!actor.isOwner()) return false;
        if (row == null) {
            State state = new State();
            state.createdAt = System.currentTimeMillis();
            state.cacheMinutes = cacheMinutes(settings.getString("cache_timeout_minutes"));
            Integer cycle = settings.upgradeWebSiteInfo().getAutoUpgradeVersion();
            state.checkSeconds = AutoUpgradeVersionType.cycle(cycle == null ? 86400 : cycle).getCycle();
            store.writeAuthorized(key, null, state, actor);
            row = store.read(key, State.class);
            if (row == null) return false;
        }
        if (row.value.complete) return true;
        for (var definition : registry.definitions()) {
            if (!definition.systemTask || plugins != PluginScheduledTaskProvider.TYPE.equals(definition.type)) continue;
            definition.requiredActions.forEach(action -> TaskAuthority.require(actor, action));
            String id = systemId(definition.type);
            if (store.task(id) != null) continue;
            StoredTask task = fresh(row.value, definition);
            task.id = id; task.ownerId = 0; task.revision = 1L;
            task.executionActions = List.copyOf(definition.requiredActions);
            store.insertDefaultTask(task, actor);
            if (store.task(id) == null) return false;
        }
        State done = row.copy(State.class); done.complete = true;
        if (store.writeAuthorized(key, row, done, actor)) return true;
        var latest = store.read(key, State.class);
        return latest != null && latest.value.complete;
    }

    static String systemId(String type) {
        return AutomationStore.ownerPrefix(0) + UUID.nameUUIDFromBytes(type.getBytes(StandardCharsets.UTF_8));
    }

    private StoredTask fresh(State state, AutomationModels.Definition definition) {
        if (definition.defaultSchedule == null || "once".equals(definition.defaultSchedule.kind)) throw new IllegalStateException("automation.invalidDefinition");
        StoredTask task = new StoredTask();
        task.name = definition.type;
        task.nameKey = definition.titleKey;
        task.type = definition.type; task.enabled = definition.enabledByDefault;
        task.createdAt = state.createdAt; task.updatedAt = state.createdAt;
        task.parameters = definition.defaultParameters.deepCopy();
        task.schedule = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(definition.defaultSchedule), AutomationModels.Schedule.class);
        if ("site.refresh-cache".equals(task.type)) task.schedule.intervalMinutes = state.cacheMinutes;
        if ("system.check-version".equals(task.type)) {
            task.schedule.intervalMinutes = Math.max(1, state.checkSeconds / 60);
            task.enabled &= state.checkSeconds > 0;
        }
        if ("interval".equals(task.schedule.kind)) task.schedule.at = state.createdAt + task.schedule.intervalMinutes * 60000L;
        TaskSchedule.validate(task.schedule);
        task.nextRunAt = TaskSchedule.nextAfter(task.schedule, state.createdAt);
        return task;
    }

    private static int cacheMinutes(String value) {
        try {
            double minutes = Double.parseDouble(value);
            if (Double.isFinite(minutes) && minutes > 0) return (int) Math.min(525600, Math.max(1, Math.ceil(minutes)));
        } catch (NumberFormatException | NullPointerException ignored) { }
        return 5;
    }

    public static class State {
        public long createdAt;
        public int cacheMinutes;
        public int checkSeconds;
        public boolean complete;
    }
}
