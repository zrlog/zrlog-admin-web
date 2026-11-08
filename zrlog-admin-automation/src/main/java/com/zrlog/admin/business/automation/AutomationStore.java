package com.zrlog.admin.business.automation;

import static com.zrlog.admin.business.automation.AssistantRecordMapper.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zrlog.admin.data.entity.*;
import com.zrlog.admin.data.repository.*;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/** Typed assistant persistence. Only bounded global configuration remains in website. */
@Singleton
public final class AutomationStore {
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String ACTOR =
            " EXISTS (SELECT 1 FROM user WHERE userId=? AND role=? AND enabled=? AND authVersion=?)";
    private final Provider<WebSiteRepository> websites;
    private final Provider<AssistantTaskRepository> tasks;
    private final Provider<AssistantTaskRunRepository> runs;
    private final Provider<AssistantNoticeReadRepository> notices;

    @Inject
    public AutomationStore(
            Provider<WebSiteRepository> websites,
            Provider<AssistantTaskRepository> tasks,
            Provider<AssistantTaskRunRepository> runs,
            Provider<AssistantNoticeReadRepository> notices) {
        this.websites = websites;
        this.tasks = tasks;
        this.runs = runs;
        this.notices = notices;
    }

    public Snapshot<StoredTask> task(String id) throws SQLException {
        requireId(id);
        var row = tasks.get().loadById(id);
        return row == null ? null : taskSnapshot(row);
    }

    /** Ownership checks do not reload parameters, snapshots or result bodies. */
    ExecutionProjection executionState(String taskId, int userId) throws SQLException {
        return tasks.get()
                .queryOne(
                        "SELECT t.id,t.version,t.activeRunId,t.leaseUntil,u.userId,u.role,u.enabled,u.authVersion"
                                + " FROM assistant_task t LEFT JOIN user u ON u.userId=? WHERE t.id=?",
                        ExecutionProjection.class,
                        userId,
                        taskId);
    }

    public static final class ExecutionProjection extends AccountAccess.Snapshot {
        public String id;
        public long version;
        public String activeRunId;
        public Timestamp leaseUntil;
    }

    public ReadBatch readBatch(Collection<String> requested) throws SQLException {
        var keys = requested.stream().distinct().collect(java.util.stream.Collectors.toList());
        if (keys.size() > 100) throw new IllegalArgumentException("automation.invalidBatch");
        Map<String, WebSite> found = new LinkedHashMap<>();
        if (!keys.isEmpty()) {
            for (var row :
                    websites.get()
                            .select(
                                    "WHERE name IN (" + placeholders(keys.size()) + ")",
                                    keys.toArray())) found.put(row.getName(), row);
        }
        return new ReadBatch(keys, found);
    }

    public static final class ReadBatch {
        private final List<String> keys;
        private final Map<String, WebSite> rows;

        private ReadBatch(List<String> keys, Map<String, WebSite> rows) {
            this.keys = keys;
            this.rows = rows;
        }

        public <T> Snapshot<T> get(String key, Class<T> type) {
            if (!keys.contains(key)) throw new IllegalArgumentException("automation.keyNotRead");
            var row = rows.get(key);
            return row == null ? null : snapshot(row, type);
        }
    }

    public List<Snapshot<StoredTask>> tasks(int ownerId, int limit, int offset)
            throws SQLException {
        return taskSnapshots(
                tasks.get()
                        .select(
                                "WHERE ownerId=? ORDER BY id LIMIT ? OFFSET ?",
                                ownerId,
                                pageSize(limit),
                                Math.max(0, offset)));
    }

    public List<StoredTask> systemTasks(List<String> ids) throws SQLException {
        if (ids.isEmpty()) return List.of();
        List<StoredTask> result = new ArrayList<>();
        for (var row :
                tasks.get()
                        .select(
                                "WHERE ownerId=0 AND id IN ("
                                        + placeholders(ids.size())
                                        + ") ORDER BY id",
                                ids.toArray())) result.add(taskValue(row));
        return result;
    }

    public List<Snapshot<StoredTask>> due(long now, int limit) throws SQLException {
        return taskSnapshots(
                tasks.get()
                        .select(
                                "WHERE dueAt<=? ORDER BY dueAt,id LIMIT ?",
                                new Timestamp(now),
                                Math.min(64, Math.max(1, limit))));
    }

    public void insertTask(StoredTask task, AccountAccess actor) throws SQLException {
        requireId(task.id);
        if (task.ownerId != actor.getUserId())
            throw new IllegalArgumentException("automation.ownerMismatch");
        if (!tasks.get()
                .insertWhen(
                        taskEntity(task),
                        ACTOR,
                        actor.getUserId(),
                        actor.getRole(),
                        true,
                        actor.getAuthVersion()))
            throw new IllegalStateException("automation.permissionChanged");
    }

    public boolean replaceTask(
            Snapshot<StoredTask> expected, StoredTask replacement, AccountAccess actor)
            throws SQLException {
        if (!expected.value.id.equals(replacement.id)
                || expected.value.ownerId != replacement.ownerId)
            throw new IllegalArgumentException("automation.taskChanged");
        var row = taskEntity(replacement);
        row.setId(null);
        String predicate = "id=? AND version=?";
        List<Object> params = new ArrayList<>(List.of(expected.value.id, expected.version));
        if (actor != null) {
            predicate += " AND" + ACTOR;
            addActor(params, actor);
        }
        return tasks.get()
                        .updateWhere(
                                row,
                                AssistantTaskRepository.CONTENT_FIELDS,
                                predicate,
                                params.toArray())
                == 1;
    }

    void insertDefaultTask(StoredTask task, AccountAccess actor) throws SQLException {
        try {
            tasks.get()
                    .insertWhen(
                            taskEntity(task),
                            ACTOR + " AND NOT EXISTS (SELECT 1 FROM assistant_task WHERE id=?)",
                            actor.getUserId(),
                            actor.getRole(),
                            true,
                            actor.getAuthVersion(),
                            task.id);
        } catch (SQLException error) {
            if (task(task.id) == null) throw error;
        }
    }

    public boolean deleteTask(Snapshot<StoredTask> expected, AccountAccess actor)
            throws SQLException {
        return tasks.get()
                        .deleteWhere(
                                "id=? AND version=? AND" + ACTOR,
                                expected.value.id,
                                expected.version,
                                actor.getUserId(),
                                actor.getRole(),
                                true,
                                actor.getAuthVersion())
                == 1;
    }

    public Snapshot<AutomationModels.Run> run(String id) throws SQLException {
        requireRunId(id);
        var row = runs.get().loadById(id);
        return row == null
                ? null
                : Snapshot.capture(
                        row.getId(), runValue(row), AutomationModels.Run.class, row.getVersion());
    }

    public void insertRun(AutomationModels.Run run) throws SQLException {
        requireRunId(run.id);
        runs.get().insert(runEntity(run));
    }

    public boolean replaceRun(
            Snapshot<AutomationModels.Run> expected, AutomationModels.Run replacement)
            throws SQLException {
        if (!expected.value.id.equals(replacement.id))
            throw new IllegalArgumentException("automation.runMismatch");
        var row = runEntity(replacement);
        row.setId(null);
        boolean changed =
                runs.get()
                                .updateWhere(
                                        row,
                                        AssistantTaskRunRepository.CONTENT_FIELDS,
                                        "id=? AND version=?",
                                        expected.value.id,
                                        expected.version)
                        == 1;
        if (changed) replacement.version = expected.version + 1;
        return changed;
    }

    public List<Snapshot<AutomationModels.Run>> runs(
            int ownerId, String taskId, int limit, int offset) throws SQLException {
        var query = visibleRunQuery(ownerId, List.of(), taskId);
        query.parameters.add(pageSize(limit));
        query.parameters.add(Math.max(0, offset));
        List<Snapshot<AutomationModels.Run>> result = new ArrayList<>();
        for (var row :
                runs.get()
                        .select(
                                "WHERE "
                                        + query.where.replace("r.", "")
                                        + " ORDER BY startedAt DESC,id DESC LIMIT ? OFFSET ?",
                                query.parameters.toArray()))
            result.add(
                    Snapshot.capture(
                            row.getId(),
                            runValue(row),
                            AutomationModels.Run.class,
                            row.getVersion()));
        return result;
    }

    public List<Snapshot<AutomationModels.Run>> visibleRuns(
            int ownerId, List<String> systemIds, String taskId, int limit, int offset)
            throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, taskId);
        return summaries(query, limit, offset);
    }

    public List<Snapshot<AutomationModels.Run>> unreadNotices(
            int ownerId, List<String> systemIds, int limit) throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, null);
        query.where +=
                " AND r.notifyOwner=? AND r.finishedAt IS NOT NULL AND NOT EXISTS (SELECT 1 FROM assistant_notice_read n WHERE n.userId=? AND n.runId=r.id)";
        query.parameters.add(true);
        query.parameters.add(ownerId);
        query.order = "r.finishedAt DESC,r.id DESC";
        return summaries(query, limit, 0);
    }

    private List<Snapshot<AutomationModels.Run>> summaries(RunQuery query, int limit, int offset)
            throws SQLException {
        query.parameters.add(pageSize(limit));
        query.parameters.add(Math.max(0, offset));
        List<Snapshot<AutomationModels.Run>> result = new ArrayList<>();
        for (var row :
                runs.get()
                        .queryList(
                                "SELECT "
                                        + RunSummary.COLUMNS
                                        + " FROM assistant_task_run r WHERE "
                                        + query.where
                                        + " ORDER BY "
                                        + query.order
                                        + " LIMIT ? OFFSET ?",
                                RunSummary.class,
                                query.parameters.toArray()))
            result.add(
                    Snapshot.capture(row.id, row.value(), AutomationModels.Run.class, row.version));
        return result;
    }

    public long visibleRunCount(int ownerId, List<String> systemIds, String taskId)
            throws SQLException {
        var query = visibleRunQuery(ownerId, systemIds, taskId);
        return ((Number)
                        runs.get()
                                .queryFirstObj(
                                        "SELECT count(*) FROM assistant_task_run r WHERE "
                                                + query.where,
                                        query.parameters.toArray()))
                .longValue();
    }

    private static RunQuery visibleRunQuery(int ownerId, List<String> systemIds, String taskId) {
        RunQuery query = new RunQuery();
        query.where = "(r.ownerId=?";
        query.parameters.add(ownerId);
        if (!systemIds.isEmpty()) {
            query.where +=
                    " OR (r.ownerId=0 AND r.taskId IN (" + placeholders(systemIds.size()) + "))";
            query.parameters.addAll(systemIds);
        }
        query.where += ")";
        if (taskId != null && !taskId.isBlank()) {
            requireId(taskId);
            query.where += " AND r.taskId=?";
            query.parameters.add(taskId);
        }
        return query;
    }

    private static final class RunQuery {
        String order = "r.startedAt DESC,r.id DESC";
        String where;
        List<Object> parameters = new ArrayList<>();
    }

    public long taskCount(int ownerId) throws SQLException {
        return ((Number)
                        tasks.get()
                                .queryFirstObj(
                                        "SELECT count(*) FROM assistant_task WHERE ownerId=?",
                                        ownerId))
                .longValue();
    }

    public long runCount(int ownerId, String taskId) throws SQLException {
        return visibleRunCount(ownerId, List.of(), taskId);
    }

    /** Only successful, completed executions expire. Failures and unresolved outcomes never do. */
    int pruneRuns(int ownerId, long now) throws SQLException {
        return pruneRuns(ownerId, now, null);
    }

    int pruneRuns(Integer ownerId, long now, String pendingUpgrade) throws SQLException {
        String predicate =
                "r.status='success' AND r.finishedAt<? AND NOT EXISTS (SELECT 1 FROM assistant_task t WHERE t.activeRunId=r.id)";
        List<Object> params = new ArrayList<>();
        params.add(dateParameter(now - java.time.Duration.ofDays(30).toMillis()));
        if (ownerId != null) {
            predicate += " AND r.ownerId=?";
            params.add(ownerId);
        }
        if (pendingUpgrade != null) {
            predicate += " AND r.id<>?";
            params.add(pendingUpgrade);
        }
        var candidates =
                runs.get()
                        .queryList(
                                "SELECT r.id,r.version FROM assistant_task_run r WHERE "
                                        + predicate
                                        + " ORDER BY r.finishedAt,r.id LIMIT 64",
                                RunKey.class,
                                params.toArray());
        int removed = 0;
        for (var row : candidates) {
            if (runs.get()
                            .deleteWhere(
                                    "id=? AND version=? AND status='success' AND NOT EXISTS (SELECT 1 FROM assistant_task t WHERE t.activeRunId=assistant_task_run.id)",
                                    row.id,
                                    row.version)
                    == 1) {
                notices.get().deleteWhere("runId=?", row.id);
                removed++;
            }
        }
        return removed;
    }

    public static final class RunKey {
        public String id;
        public long version;
    }

    boolean markRunRead(Snapshot<AutomationModels.Run> run, AccountAccess actor)
            throws SQLException {
        AssistantNoticeRead row = new AssistantNoticeRead();
        row.setId(noticeReadKey(actor.getUserId(), run.value.id));
        row.setRunId(run.value.id);
        row.setUserId(actor.getUserId());
        row.setReadAt(new Timestamp(System.currentTimeMillis()));
        try {
            boolean inserted =
                    notices.get()
                            .insertWhen(
                                    row,
                                    ACTOR
                                            + " AND EXISTS (SELECT 1 FROM assistant_task_run WHERE id=? AND version=?) AND NOT EXISTS (SELECT 1 FROM assistant_notice_read WHERE id=?)",
                                    actor.getUserId(),
                                    actor.getRole(),
                                    true,
                                    actor.getAuthVersion(),
                                    run.value.id,
                                    run.version,
                                    row.getId());
            return inserted || notices.get().loadById(row.getId()) != null;
        } catch (SQLException error) {
            if (notices.get().loadById(row.getId()) == null) throw error;
            return true;
        }
    }

    static String noticeReadKey(int userId, String runId) {
        return runId + ":" + userId;
    }

    boolean deleteRun(Snapshot<AutomationModels.Run> run, AccountAccess actor) throws SQLException {
        int deleted =
                runs.get()
                        .deleteWhere(
                                "id=? AND version=? AND status<>'running' AND finishedAt IS NOT NULL AND NOT EXISTS (SELECT 1 FROM assistant_task t WHERE t.activeRunId=assistant_task_run.id) AND"
                                        + ACTOR,
                                run.value.id,
                                run.version,
                                actor.getUserId(),
                                actor.getRole(),
                                true,
                                actor.getAuthVersion());
        if (deleted == 1) notices.get().deleteWhere("runId=?", run.value.id);
        return deleted == 1;
    }

    private static void addActor(List<Object> params, AccountAccess actor) {
        params.add(actor.getUserId());
        params.add(actor.getRole());
        params.add(true);
        params.add(actor.getAuthVersion());
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static List<Snapshot<StoredTask>> taskSnapshots(List<AssistantTask> rows) {
        List<Snapshot<StoredTask>> result = new ArrayList<>();
        for (var row : rows) result.add(taskSnapshot(row));
        return result;
    }

    private static Snapshot<StoredTask> taskSnapshot(AssistantTask row) {
        return Snapshot.capture(row.getId(), taskValue(row), StoredTask.class, row.getVersion());
    }

    public <T> Snapshot<T> read(String key, Class<T> type) throws SQLException {
        WebSite row = websites.get().findByName(key);
        return row == null ? null : snapshot(row, type);
    }

    public <T> boolean write(String key, Snapshot<T> expected, T value) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get().updateValueWhere(json, "name=? AND value=?", key, expected.raw)
                    == 1;
        }
        try {
            websites.get().insert(WebSiteRepository.entry(key, json, null));
            return true;
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    public <T> boolean writeAuthorized(
            String key, Snapshot<T> expected, T value, AccountAccess actor) throws SQLException {
        String json = JSON.toJson(value);
        if (expected != null) {
            return websites.get()
                            .updateValueWhere(
                                    json,
                                    "name=? AND value=? AND" + ACTOR,
                                    key,
                                    expected.raw,
                                    actor.getUserId(),
                                    actor.getRole(),
                                    true,
                                    actor.getAuthVersion())
                    == 1;
        }
        try {
            return websites.get()
                    .insertWhen(
                            WebSiteRepository.entry(key, json, null),
                            ACTOR,
                            actor.getUserId(),
                            actor.getRole(),
                            true,
                            actor.getAuthVersion());
        } catch (SQLException error) {
            if (read(key, value.getClass()) == null) throw error;
            return false;
        }
    }

    private static <T> Snapshot<T> snapshot(WebSite row, Class<T> type) {
        String raw = Objects.toString(row.getValue(), "");
        T value = JSON.fromJson(raw, type);
        if (value == null) throw new IllegalStateException("automation.invalidStoredRecord");
        return new Snapshot<>(row.getName(), raw, value, row.getVersion());
    }

    static String ownerPrefix(int ownerId) {
        if (ownerId < 0) throw new IllegalArgumentException("automation.invalidOwner");
        return String.format(Locale.ROOT, "%010d.", ownerId);
    }

    static String timeKey(long millis) {
        if (millis < 0) throw new IllegalArgumentException("automation.invalidTime");
        return String.format(Locale.ROOT, "%020d", millis);
    }

    private static int pageSize(int requested) {
        return Math.max(1, Math.min(100, requested));
    }

    static void requireId(String id) {
        if (id == null || !id.matches("[0-9]{10}\\.[0-9a-f-]{36}"))
            throw new IllegalArgumentException("automation.invalidId");
    }

    static void requireRunId(String id) {
        if (id == null || !id.matches("[0-9]{10}\\.[0-9]{20}\\.[0-9a-f-]{36}"))
            throw new IllegalArgumentException("automation.invalidId");
    }

    public static final class Snapshot<T> {
        public final String key;
        public final String raw;
        public final T value;
        public final long version;

        Snapshot(String key, String raw, T value, long version) {
            this.key = key;
            this.raw = raw;
            this.value = value;
            this.version = version;
        }

        public T copy(Class<T> type) {
            return JSON.fromJson(raw, type);
        }

        static <T> Snapshot<T> capture(String key, T value, Class<T> type) {
            return capture(key, value, type, 0);
        }

        static <T> Snapshot<T> capture(String key, T value, Class<T> type, long version) {
            String raw = JSON.toJson(value);
            return new Snapshot<>(key, raw, JSON.fromJson(raw, type), version);
        }
    }
}
