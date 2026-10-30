package com.zrlog.admin.business.automation;

import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Migrates existing timers once; plugin schedules remain plugin-owned behind one parent trigger. */
@Singleton
public final class AutomationDefaults {
    static final String KEY = "assistant.defaults";
    static final String PLUGIN_KEY = "assistant.plugin-defaults";
    private final AutomationStore store;
    private final Provider<User> users;
    private final WebsiteKvService settings;

    @Inject
    public AutomationDefaults(AutomationStore store, Provider<User> users, WebsiteKvService settings) {
        this.store = store;
        this.users = users;
        this.settings = settings;
    }

    public boolean initialize() throws SQLException { return initialize(KEY, false); }

    public boolean initializePluginTrigger() throws SQLException { return initialize(PLUGIN_KEY, true); }

    private boolean initialize(String key, boolean plugins) throws SQLException {
        AutomationStore.Snapshot<State> row = store.read(key, State.class);
        if (row != null && row.value.complete) return true;
        if (row == null) {
            AccountAccess owner = AccountAccess.from(users.get().queryFirstWithParams(
                    "SELECT userId,role,enabled,authVersion FROM user WHERE role=? AND enabled=? ORDER BY userId LIMIT 1", "owner", true));
            if (!owner.isOwner()) return false;
            State state = new State();
            state.ownerId = owner.getUserId();
            state.createdAt = System.currentTimeMillis();
            if (!plugins) {
                state.cacheMinutes = cacheMinutes(settings.getString("cache_timeout_minutes"));
                Integer legacyCycle = settings.upgradeWebSiteInfo().getAutoUpgradeVersion();
                state.checkSeconds = AutoUpgradeVersionType.cycle(legacyCycle == null ? 86400 : legacyCycle).getCycle();
            }
            store.writeAuthorized(key, null, state, owner);
            row = store.read(key, State.class);
            if (row == null) return false;
        }
        if (row.value.complete) return true;
        AccountAccess owner = AccountAccess.from(users.get().loadById(row.value.ownerId));
        if (!owner.isOwner()) return false;
        if (plugins) {
            seed(row, owner, PluginScheduledTaskProvider.TYPE, "plugins", 1, true);
        } else {
            seed(row, owner, "site.refresh-cache", "cache", row.value.cacheMinutes, true);
            seed(row, owner, "system.check-version", "version", Math.max(1, row.value.checkSeconds / 60), row.value.checkSeconds > 0);
        }
        State done = row.copy(State.class);
        done.complete = true;
        if (store.writeAuthorized(key, row, done, owner)) return true;
        AutomationStore.Snapshot<State> latest = store.read(key, State.class);
        return latest != null && latest.value.complete;
    }

    private void seed(AutomationStore.Snapshot<State> state, AccountAccess actor, String type, String label,
                      int intervalMinutes, boolean enabled) throws SQLException {
        StoredTask task = new StoredTask();
        task.id = AutomationStore.ownerPrefix(actor.getUserId()) + UUID.nameUUIDFromBytes(type.getBytes(StandardCharsets.UTF_8));
        task.ownerId = actor.getUserId();
        task.ownerRole = actor.getRole();
        task.ownerAuthVersion = actor.getAuthVersion();
        task.name = I18nUtil.getAdminBackendStringFromRes("admin.automation." + label + ".title");
        task.type = type;
        task.revision = 1L;
        task.enabled = enabled;
        task.createdAt = state.value.createdAt;
        task.updatedAt = state.value.createdAt;
        task.authorizedActions = List.of((PluginScheduledTaskProvider.TYPE.equals(type)
                ? AccountAction.PLUGIN_MANAGE : AccountAction.SYSTEM_MANAGE).getId());
        task.schedule = new AutomationModels.Schedule();
        task.schedule.kind = "interval";
        task.schedule.intervalMinutes = intervalMinutes;
        task.schedule.at = state.value.createdAt + intervalMinutes * 60000L;
        task.nextRunAt = task.schedule.at;
        store.insertDefaultTask(task, actor, state);
    }

    private static int cacheMinutes(String value) {
        try {
            double minutes = Double.parseDouble(value);
            if (Double.isFinite(minutes) && minutes > 0) return (int) Math.min(525600, Math.max(1, Math.ceil(minutes)));
        } catch (NumberFormatException | NullPointerException ignored) { }
        return 5;
    }

    public static class State {
        public int ownerId;
        public long createdAt;
        public int cacheMinutes;
        public int checkSeconds;
        public boolean complete;
    }
}
