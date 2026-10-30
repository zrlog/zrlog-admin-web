package com.zrlog.admin.business.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One row per task/run, conditional single-statement writes on both JDBC and WebAPI databases. */
@Singleton
public final class AutomationStore {
    static final String TASK_PREFIX = "assistant.v1.task:";
    static final String RUN_PREFIX = "assistant.v1.run:";
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String ACTOR = " EXISTS (SELECT 1 FROM user WHERE userId=? AND role=? AND enabled=? AND authVersion=?)";
    private final Provider<WebSite> websites;

    @Inject
    public AutomationStore(Provider<WebSite> websites) { this.websites = Objects.requireNonNull(websites); }

    public Snapshot<StoredTask> task(String id) throws SQLException {
        requireId(id);
        return read(TASK_PREFIX + id, StoredTask.class);
    }

    public List<Snapshot<StoredTask>> tasks(int ownerId, int limit, int offset) throws SQLException {
        return list(TASK_PREFIX + ownerPrefix(ownerId), StoredTask.class, limit, offset, false);
    }

    public List<Snapshot<StoredTask>> due(long now, int limit) throws SQLException {
        return rows(websites.get().queryListWithParams(
                "SELECT name,value FROM website WHERE name>=? AND name<? AND remark<=? ORDER BY remark,name LIMIT ?",
                TASK_PREFIX, end(TASK_PREFIX), timeKey(now), Math.min(64, Math.max(1, limit))), StoredTask.class);
    }

    public void insertTask(StoredTask task, AccountAccess actor) throws SQLException {
        requireId(task.id);
        if (task.ownerId != actor.getUserId()) throw new IllegalArgumentException("automation.ownerMismatch");
        boolean inserted = websites.get().execute(
                "INSERT INTO website(name,value,remark) SELECT ?,?,? WHERE" + ACTOR,
                TASK_PREFIX + task.id, JSON.toJson(task), dueKey(task), actor.getUserId(), actor.getRole(),
                true, actor.getAuthVersion());
        if (!inserted) throw new IllegalStateException("automation.permissionChanged");
    }

    public boolean replaceTask(Snapshot<StoredTask> expected, StoredTask replacement, AccountAccess actor) throws SQLException {
        if (!expected.value.id.equals(replacement.id) || expected.value.ownerId != replacement.ownerId) {
            throw new IllegalArgumentException("automation.ownerMismatch");
        }
        String sql = "UPDATE website SET value=?,remark=? WHERE name=? AND value=?";
        List<Object> parameters = new ArrayList<>(List.of(JSON.toJson(replacement), dueKey(replacement), expected.key, expected.raw));
        if (actor != null) {
            sql += " AND" + ACTOR;
            parameters.add(actor.getUserId());
            parameters.add(actor.getRole());
            parameters.add(true);
            parameters.add(actor.getAuthVersion());
        }
        return websites.get().execute(sql, parameters.toArray());
    }

    /** Insert-only initialization cannot reset an existing site configuration. */
    void insertDefaultTask(StoredTask task, AccountAccess actor) throws SQLException {
        String key = TASK_PREFIX + task.id;
        try {
            websites.get().execute("INSERT INTO website(name,value,remark) SELECT ?,?,? WHERE" + ACTOR
                            + " AND NOT EXISTS (SELECT 1 FROM website WHERE name=?)",
                    key, JSON.toJson(task), dueKey(task), actor.getUserId(), actor.getRole(), true, actor.getAuthVersion(),
                    key);
        } catch (SQLException error) {
            if (read(key, StoredTask.class) == null) throw error;
        }
    }

    public boolean deleteTask(Snapshot<StoredTask> expected, AccountAccess actor) throws SQLException {
        return websites.get().execute("DELETE FROM website WHERE name=? AND value=? AND" + ACTOR,
                expected.key, expected.raw, actor.getUserId(), actor.getRole(), true, actor.getAuthVersion());
    }

    public Snapshot<AutomationModels.Run> run(String id) throws SQLException {
        requireRunId(id);
        return read(RUN_PREFIX + id, AutomationModels.Run.class);
    }

    public void insertRun(AutomationModels.Run run) throws SQLException {
        requireRunId(run.id);
        // Only a task's successful claimant calls this before any business action.
        websites.get().execute("INSERT INTO website(name,value,remark) VALUES(?,?,?)",
                RUN_PREFIX + run.id, JSON.toJson(run), run.taskId);
    }

    public boolean replaceRun(Snapshot<AutomationModels.Run> expected, AutomationModels.Run replacement) throws SQLException {
        if (!expected.value.id.equals(replacement.id)) throw new IllegalArgumentException("automation.runMismatch");
        return websites.get().execute("UPDATE website SET value=? WHERE name=? AND value=?",
                JSON.toJson(replacement), expected.key, expected.raw);
    }

    public List<Snapshot<AutomationModels.Run>> runs(int ownerId, String taskId, int limit, int offset) throws SQLException {
        if (taskId == null || taskId.isBlank()) {
            return list(RUN_PREFIX + ownerPrefix(ownerId), AutomationModels.Run.class, limit, offset, true);
        }
        requireId(taskId);
        String prefix = RUN_PREFIX + ownerPrefix(ownerId);
        return rows(websites.get().queryListWithParams(
                "SELECT name,value FROM website WHERE name>=? AND name<? AND remark=? ORDER BY name DESC LIMIT ? OFFSET ?",
                prefix, end(prefix), taskId, pageSize(limit), Math.max(0, offset)), AutomationModels.Run.class);
    }

    public List<Snapshot<AutomationModels.Run>> visibleRuns(int ownerId, List<String> systemIds, String taskId, int limit, int offset) throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, taskId);
        query.parameters.add(pageSize(limit)); query.parameters.add(Math.max(0, offset));
        return rows(websites.get().queryListWithParams("SELECT name,value FROM website WHERE " + query.where
                + " ORDER BY SUBSTR(name," + (RUN_PREFIX.length() + 12) + ") DESC LIMIT ? OFFSET ?", query.parameters.toArray()), AutomationModels.Run.class);
    }

    public long visibleRunCount(int ownerId, List<String> systemIds, String taskId) throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, taskId);
        return ((Number) websites.get().queryFirstObj("SELECT count(*) FROM website WHERE " + query.where, query.parameters.toArray())).longValue();
    }

    private static RunQuery visibleRunQuery(int ownerId, List<String> systemIds, String taskId) {
        String prefix = RUN_PREFIX + ownerPrefix(ownerId);
        RunQuery query = new RunQuery();
        query.where = "((name>=? AND name<?)";
        query.parameters.add(prefix); query.parameters.add(end(prefix));
        if (!systemIds.isEmpty()) {
            query.where += " OR (name>=? AND name<? AND remark IN (" + String.join(",", java.util.Collections.nCopies(systemIds.size(), "?")) + "))";
            query.parameters.add(RUN_PREFIX); query.parameters.add(end(RUN_PREFIX)); query.parameters.addAll(systemIds);
        }
        query.where += ")";
        if (taskId != null && !taskId.isBlank()) {
            requireId(taskId); query.where += " AND remark=?"; query.parameters.add(taskId);
        }
        return query;
    }

    private static class RunQuery {
        String where;
        List<Object> parameters = new ArrayList<>();
    }

    public long taskCount(int ownerId) throws SQLException {
        String prefix = TASK_PREFIX + ownerPrefix(ownerId);
        return ((Number) websites.get().queryFirstObj("SELECT count(*) FROM website WHERE name>=? AND name<?", prefix, end(prefix))).longValue();
    }

    public long runCount(int ownerId, String taskId) throws SQLException {
        String prefix = RUN_PREFIX + ownerPrefix(ownerId);
        Object value = taskId == null || taskId.isBlank()
                ? websites.get().queryFirstObj("SELECT count(*) FROM website WHERE name>=? AND name<?", prefix, end(prefix))
                : websites.get().queryFirstObj("SELECT count(*) FROM website WHERE name>=? AND name<? AND remark=?", prefix, end(prefix), taskId);
        return ((Number) value).longValue();
    }

    /** Bounded retention sweep; active executions and another account's history are never removed. */
    public int pruneRuns(int ownerId, long now) throws SQLException {
        var upgrade = read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        String pendingUpgrade = upgrade != null && !"confirmed".equals(upgrade.value.state) ? upgrade.value.runId : null;
        String prefix = RUN_PREFIX + ownerPrefix(ownerId);
        List<Snapshot<AutomationModels.Run>> candidates = rows(websites.get().queryListWithParams(
                "SELECT name,value FROM website WHERE name>=? AND name<? ORDER BY name DESC LIMIT ?",
                prefix, end(prefix), 1000), AutomationModels.Run.class);
        int completed = 0;
        int removed = 0;
        long oldest = now - java.time.Duration.ofDays(90).toMillis();
        for (Snapshot<AutomationModels.Run> row : candidates) {
            if ("running".equals(row.value.status) || row.value.finishedAt == null
                    || Objects.equals(row.value.id, pendingUpgrade)) continue;
            completed++;
            if (completed <= 500 && row.value.finishedAt >= oldest) continue;
            if (websites.get().execute("DELETE FROM website WHERE name=? AND value=?", row.key, row.raw)) {
                String reads = readKeyPrefix(row.value.id);
                websites.get().execute("DELETE FROM website WHERE name>=? AND name<?", reads, end(reads));
                removed++;
            }
            if (removed >= 64) break;
        }
        return removed;
    }

    static String noticeReadKey(int userId, String runId) { return readKeyPrefix(runId) + userId; }
    private static String readKeyPrefix(String runId) { return "assistant.v1.notice-read:" + runId + ":"; }

    boolean markRunRead(Snapshot<AutomationModels.Run> run, AccountAccess actor) throws SQLException {
        String key = noticeReadKey(actor.getUserId(), run.value.id);
        try {
            return websites.get().execute("INSERT INTO website(name,value) SELECT ?,? WHERE" + ACTOR
                            + " AND EXISTS (SELECT 1 FROM website WHERE name=? AND value=?)",
                    key, JSON.toJson(System.currentTimeMillis()), actor.getUserId(), actor.getRole(), true,
                    actor.getAuthVersion(), run.key, run.raw);
        } catch (SQLException error) {
            if (read(key, Long.class) == null) throw error;
            return true;
        }
    }

    public <T> Snapshot<T> read(String key, Class<T> type) throws SQLException {
        Map<String, Object> row = websites.get().queryFirstWithParams("SELECT name,value FROM website WHERE name=?", key);
        return row == null ? null : snapshot(row, type);
    }

    public <T> boolean write(String key, Snapshot<T> expected, T value) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get().execute("UPDATE website SET value=? WHERE name=? AND value=?", json, key, expected.raw);
        }
        try {
            return websites.get().execute("INSERT INTO website(name,value) VALUES(?,?)", key, json);
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    public <T> boolean writeAuthorized(String key, Snapshot<T> expected, T value, AccountAccess actor) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get().execute("UPDATE website SET value=? WHERE name=? AND value=? AND" + ACTOR,
                    json, key, expected.raw, actor.getUserId(), actor.getRole(), true, actor.getAuthVersion());
        }
        try {
            return websites.get().execute("INSERT INTO website(name,value) SELECT ?,? WHERE" + ACTOR,
                    key, json, actor.getUserId(), actor.getRole(), true, actor.getAuthVersion());
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    private <T> List<Snapshot<T>> list(String prefix, Class<T> type, int limit, int offset, boolean newest) throws SQLException {
        return rows(websites.get().queryListWithParams(
                "SELECT name,value FROM website WHERE name>=? AND name<? ORDER BY name " + (newest ? "DESC" : "ASC")
                        + " LIMIT ? OFFSET ?", prefix, end(prefix), pageSize(limit), Math.max(0, offset)), type);
    }

    private static <T> List<Snapshot<T>> rows(List<Map<String, Object>> rows, Class<T> type) {
        List<Snapshot<T>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(snapshot(row, type));
        return result;
    }

    private static <T> Snapshot<T> snapshot(Map<String, Object> row, Class<T> type) {
        String raw = Objects.toString(row.get("value"), "");
        T value = JSON.fromJson(raw, type);
        if (value == null) throw new IllegalStateException("automation.invalidStoredRecord");
        return new Snapshot<>(Objects.toString(row.get("name")), raw, value);
    }

    static String dueKey(StoredTask task) {
        if (task.activeRun != null) return timeKey(task.leaseUntil);
        return task.enabled && task.nextRunAt != null ? timeKey(task.nextRunAt) : "~";
    }

    static String ownerPrefix(int ownerId) {
        if (ownerId < 0) throw new IllegalArgumentException("automation.invalidOwner");
        return String.format(Locale.ROOT, "%010d.", ownerId);
    }

    static String timeKey(long millis) {
        if (millis < 0) throw new IllegalArgumentException("automation.invalidTime");
        return String.format(Locale.ROOT, "%020d", millis);
    }

    private static String end(String prefix) {
        return prefix.substring(0, prefix.length() - 1) + (char) (prefix.charAt(prefix.length() - 1) + 1);
    }

    private static int pageSize(int requested) { return Math.max(1, Math.min(100, requested)); }

    static void requireId(String id) {
        if (id == null || !id.matches("[0-9]{10}\\.[0-9a-f-]{36}")) throw new IllegalArgumentException("automation.invalidId");
    }

    static void requireRunId(String id) {
        if (id == null || !id.matches("[0-9]{10}\\.[0-9]{20}\\.[0-9a-f-]{36}")) throw new IllegalArgumentException("automation.invalidId");
    }

    public static final class Snapshot<T> {
        public final String key;
        public final String raw;
        public final T value;

        Snapshot(String key, String raw, T value) {
            this.key = key;
            this.raw = raw;
            this.value = value;
        }

        public T copy(Class<T> type) { return JSON.fromJson(raw, type); }
    }
}
