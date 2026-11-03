package com.zrlog.admin.business.automation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private final Provider<WebSiteRepository> websites;

    @Inject
    public AutomationStore(Provider<WebSiteRepository> websites) { this.websites = Objects.requireNonNull(websites); }

    public Snapshot<StoredTask> task(String id) throws SQLException {
        requireId(id);
        return read(TASK_PREFIX + id, StoredTask.class);
    }

    ExecutionState executionState(String taskId, int userId) throws SQLException {
        requireId(taskId);
        var row = websites.get().queryOne(
                "SELECT w.name,w.value,u.userId,u.role,u.enabled,u.authVersion FROM website w"
                        + " LEFT JOIN user u ON u.userId=? WHERE w.name=?", ExecutionProjection.class, userId, TASK_PREFIX + taskId);
        return row == null ? null : new ExecutionState(snapshot(row.name, row.value, StoredTask.class),
                AccountAccess.fromSnapshot(row));
    }

    static final class ExecutionProjection extends AccountAccess.Snapshot {
        String name;
        String value;
    }

    static final class ExecutionState {
        private final Snapshot<StoredTask> task;
        private final AccountAccess actor;

        ExecutionState(Snapshot<StoredTask> task, AccountAccess actor) {
            this.task = task;
            this.actor = actor;
        }

        Snapshot<StoredTask> task() { return task; }
        AccountAccess actor() { return actor; }
    }

    /** Bounded reads shared only by the operation that requested these exact keys. */
    public ReadBatch readBatch(Collection<String> requested) throws SQLException {
        var keys = requested.stream().distinct().collect(java.util.stream.Collectors.toList());
        if (keys.size() > 100) throw new IllegalArgumentException("automation.invalidBatch");
        Map<String, WebSite> found = new LinkedHashMap<>();
        if (!keys.isEmpty()) {
            var placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
            for (var row : websites.get().select(
                    "WHERE name IN (" + placeholders + ")", keys.toArray())) {
                found.put(Objects.toString(row.getName()), row);
            }
        }
        return new ReadBatch(keys, found);
    }

    public static final class ReadBatch {
        private final List<String> keys;
        private final Map<String, WebSite> rows;

        private ReadBatch(List<String> keys, Map<String, WebSite> rows) {
            this.keys = keys; this.rows = rows;
        }

        public <T> Snapshot<T> get(String key, Class<T> type) {
            if (!keys.contains(key)) throw new IllegalArgumentException("automation.keyNotRead");
            var row = rows.get(key);
            return row == null ? null : snapshot(row, type);
        }
    }

    public List<Snapshot<StoredTask>> tasks(int ownerId, int limit, int offset) throws SQLException {
        return list(TASK_PREFIX + ownerPrefix(ownerId), StoredTask.class, limit, offset, false);
    }

    public List<Snapshot<StoredTask>> due(long now, int limit) throws SQLException {
        return rows(websites.get().select(
                "WHERE name>=? AND name<? AND remark<=? ORDER BY remark,name LIMIT ?",
                TASK_PREFIX, end(TASK_PREFIX), timeKey(now), Math.min(64, Math.max(1, limit))), StoredTask.class);
    }

    public void insertTask(StoredTask task, AccountAccess actor) throws SQLException {
        requireId(task.id);
        if (task.ownerId != actor.getUserId()) throw new IllegalArgumentException("automation.ownerMismatch");
        boolean inserted = websites.get().insertWhen(
                WebSiteRepository.entry(TASK_PREFIX + task.id, JSON.toJson(task), dueKey(task)), ACTOR,
                actor.getUserId(), actor.getRole(), true, actor.getAuthVersion());
        if (!inserted) throw new IllegalStateException("automation.permissionChanged");
    }

    public boolean replaceTask(Snapshot<StoredTask> expected, StoredTask replacement, AccountAccess actor) throws SQLException {
        if (!expected.value.id.equals(replacement.id) || expected.value.ownerId != replacement.ownerId) {
            throw new IllegalArgumentException("automation.ownerMismatch");
        }
        String predicate = "name=? AND value=?";
        List<Object> parameters = new ArrayList<>(List.of(expected.key, expected.raw));
        if (actor != null) {
            predicate += " AND" + ACTOR;
            parameters.add(actor.getUserId());
            parameters.add(actor.getRole());
            parameters.add(true);
            parameters.add(actor.getAuthVersion());
        }
        return websites.get().updateContentWhere(JSON.toJson(replacement), dueKey(replacement),
                predicate, parameters.toArray()) == 1;
    }

    /** Insert-only initialization cannot reset an existing site configuration. */
    void insertDefaultTask(StoredTask task, AccountAccess actor) throws SQLException {
        String key = TASK_PREFIX + task.id;
        try {
            websites.get().insertWhen(WebSiteRepository.entry(key, JSON.toJson(task), dueKey(task)),
                    ACTOR + " AND NOT EXISTS (SELECT 1 FROM website WHERE name=?)",
                    actor.getUserId(), actor.getRole(), true, actor.getAuthVersion(), key);
        } catch (SQLException error) {
            if (read(key, StoredTask.class) == null) throw error;
        }
    }

    public boolean deleteTask(Snapshot<StoredTask> expected, AccountAccess actor) throws SQLException {
        return websites.get().deleteWhere("name=? AND value=? AND" + ACTOR,
                expected.key, expected.raw, actor.getUserId(), actor.getRole(), true, actor.getAuthVersion()) == 1;
    }

    public Snapshot<AutomationModels.Run> run(String id) throws SQLException {
        requireRunId(id);
        return read(RUN_PREFIX + id, AutomationModels.Run.class);
    }

    public void insertRun(AutomationModels.Run run) throws SQLException {
        requireRunId(run.id);
        // Only a task's successful claimant calls this before any business action.
        websites.get().insert(WebSiteRepository.entry(RUN_PREFIX + run.id, JSON.toJson(run), run.taskId));
    }

    public boolean replaceRun(Snapshot<AutomationModels.Run> expected, AutomationModels.Run replacement) throws SQLException {
        if (!expected.value.id.equals(replacement.id)) throw new IllegalArgumentException("automation.runMismatch");
        return websites.get().updateValueWhere(JSON.toJson(replacement), "name=? AND value=?",
                expected.key, expected.raw) == 1;
    }

    public List<Snapshot<AutomationModels.Run>> runs(int ownerId, String taskId, int limit, int offset) throws SQLException {
        if (taskId == null || taskId.isBlank()) {
            return list(RUN_PREFIX + ownerPrefix(ownerId), AutomationModels.Run.class, limit, offset, true);
        }
        requireId(taskId);
        String prefix = RUN_PREFIX + ownerPrefix(ownerId);
        return rows(websites.get().select(
                "WHERE name>=? AND name<? AND remark=? ORDER BY name DESC LIMIT ? OFFSET ?",
                prefix, end(prefix), taskId, pageSize(limit), Math.max(0, offset)), AutomationModels.Run.class);
    }

    public List<Snapshot<AutomationModels.Run>> visibleRuns(int ownerId, List<String> systemIds, String taskId, int limit, int offset) throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, taskId);
        query.parameters.add(pageSize(limit)); query.parameters.add(Math.max(0, offset));
        return rows(websites.get().select("WHERE " + query.where
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
        List<Snapshot<AutomationModels.Run>> candidates = rows(websites.get().select(
                "WHERE name>=? AND name<? ORDER BY name DESC LIMIT ?",
                prefix, end(prefix), 1000), AutomationModels.Run.class);
        int completed = 0;
        int removed = 0;
        long oldest = now - java.time.Duration.ofDays(90).toMillis();
        for (Snapshot<AutomationModels.Run> row : candidates) {
            if ("running".equals(row.value.status) || row.value.finishedAt == null
                    || Objects.equals(row.value.id, pendingUpgrade)) continue;
            completed++;
            if (completed <= 500 && row.value.finishedAt >= oldest) continue;
            if (websites.get().deleteWhere("name=? AND value=?", row.key, row.raw) == 1) {
                String reads = readKeyPrefix(row.value.id);
                websites.get().deleteWhere("name>=? AND name<?", reads, end(reads));
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
            return websites.get().insertWhen(WebSiteRepository.entry(key, JSON.toJson(System.currentTimeMillis()), null),
                    ACTOR + " AND EXISTS (SELECT 1 FROM website WHERE name=? AND value=?)",
                    actor.getUserId(), actor.getRole(), true, actor.getAuthVersion(), run.key, run.raw);
        } catch (SQLException error) {
            if (read(key, Long.class) == null) throw error;
            return true;
        }
    }

    public <T> Snapshot<T> read(String key, Class<T> type) throws SQLException {
        WebSite row = websites.get().findByName(key);
        return row == null ? null : snapshot(row, type);
    }

    public <T> boolean write(String key, Snapshot<T> expected, T value) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get().updateValueWhere(json, "name=? AND value=?", key, expected.raw) == 1;
        }
        try {
            websites.get().insert(WebSiteRepository.entry(key, json, null));
            return true;
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    public <T> boolean writeAuthorized(String key, Snapshot<T> expected, T value, AccountAccess actor) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get().updateValueWhere(json, "name=? AND value=? AND" + ACTOR,
                    key, expected.raw, actor.getUserId(), actor.getRole(), true, actor.getAuthVersion()) == 1;
        }
        try {
            return websites.get().insertWhen(WebSiteRepository.entry(key, json, null), ACTOR,
                    actor.getUserId(), actor.getRole(), true, actor.getAuthVersion());
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    private <T> List<Snapshot<T>> list(String prefix, Class<T> type, int limit, int offset, boolean newest) throws SQLException {
        return rows(websites.get().select(
                "WHERE name>=? AND name<? ORDER BY name " + (newest ? "DESC" : "ASC")
                        + " LIMIT ? OFFSET ?", prefix, end(prefix), pageSize(limit), Math.max(0, offset)), type);
    }

    private static <T> List<Snapshot<T>> rows(List<WebSite> rows, Class<T> type) {
        List<Snapshot<T>> result = new ArrayList<>();
        for (WebSite row : rows) result.add(snapshot(row, type));
        return result;
    }

    private static <T> Snapshot<T> snapshot(WebSite row, Class<T> type) {
        return snapshot(row.getName(), row.getValue(), type);
    }

    private static <T> Snapshot<T> snapshot(String name, String rawValue, Class<T> type) {
        String raw = Objects.toString(rawValue, "");
        T value = JSON.fromJson(raw, type);
        if (value == null) throw new IllegalStateException("automation.invalidStoredRecord");
        return new Snapshot<>(name, raw, value);
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

        static <T> Snapshot<T> capture(String key, T value, Class<T> type) {
            String raw = JSON.toJson(value);
            return new Snapshot<>(key, raw, JSON.fromJson(raw, type));
        }
    }
}
