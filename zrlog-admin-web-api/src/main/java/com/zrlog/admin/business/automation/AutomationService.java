package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.data.security.AccountAccess;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Site and personal task management; task enablement never grants permissions or starts an execution. */
public final class AutomationService {
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final TaskRegistry registry;
    private final LongSupplier clock;

    public AutomationService(AutomationStore store, TaskAuthority authority, TaskRegistry registry, LongSupplier clock) {
        this.store = store;
        this.authority = authority;
        this.registry = registry;
        this.clock = clock;
    }

    public List<AutomationModels.Definition> definitions(AccountAccess actor) {
        AccountAccess current = authority.current(actor);
        return registry.definitions().stream().filter(definition -> {
            try {
                definition.requiredActions.forEach(action -> TaskAuthority.require(current, action));
                return true;
            } catch (PermissionErrorException ignored) {
                return false;
            }
        }).collect(Collectors.toList());
    }

    List<String> requiredActions(String type) { return registry.require(type).definition.requiredActions; }

    public List<StoredTask> tasks(AccountAccess actor, int offset) throws SQLException {
        actor = authority.current(actor);
        return store.tasks(actor.getUserId(), 100, offset).stream().map(row -> row.value).collect(Collectors.toList());
    }

    String instanceId(AccountAccess actor, String type, com.google.gson.JsonObject parameters) {
        actor = authority.current(actor);
        var entry = registry.require(type);
        for (String action : entry.definition.requiredActions) TaskAuthority.require(actor, action);
        String key = entry.provider.instanceKey(type, parameters);
        return AutomationStore.ownerPrefix(actor.getUserId()) + (key == null ? UUID.randomUUID()
                : UUID.nameUUIDFromBytes((type + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public StoredTask save(AccountAccess actor, AutomationModels.Plan plan) throws Exception {
        actor = authority.current(actor);
        if (plan == null || plan.name == null || plan.name.isBlank() || plan.name.length() > 160
                || plan.parameters == null || plan.parameters.toString().length() > 32768) {
            throw new IllegalArgumentException("automation.invalidPlan");
        }
        TaskRegistry.Entry entry = registry.require(plan.type);
        if (!entry.definition.available) throw new IllegalArgumentException("automation.taskUnavailable");
        TaskSchedule.validate(plan.schedule);
        if (entry.definition.systemTask && "once".equals(plan.schedule.kind)) throw new IllegalArgumentException("automation.invalidSchedule");
        AutomationStore.Snapshot<StoredTask> previous = plan.id == null ? null : owned(actor, plan.id);
        if (entry.definition.systemTask && previous == null) throw new IllegalArgumentException("automation.systemTaskManaged");
        if (previous != null && (!previous.value.type.equals(plan.type)
                || !Objects.equals(entry.provider.instanceKey(plan.type, previous.value.parameters),
                                   entry.provider.instanceKey(plan.type, plan.parameters)))) {
            throw new IllegalArgumentException("automation.invalidPlan");
        }
        if (previous != null && (!Objects.equals(previous.value.revision, plan.revision) || previous.value.activeRun != null)) {
            throw new IllegalStateException("automation.taskChanged");
        }
        long now = clock.getAsLong();
        Long next = TaskSchedule.nextAfter(plan.schedule, now - 1);
        if (next == null && plan.enabled) throw new IllegalArgumentException("automation.timePassed");
        StoredTask task = new StoredTask();
        task.id = previous == null ? instanceId(actor, plan.type, plan.parameters) : previous.value.id;
        if (previous == null && store.task(task.id) != null) throw new IllegalStateException("automation.taskAlreadyExists");
        task.ownerId = previous == null ? actor.getUserId() : previous.value.ownerId;
        task.updatedBy = actor.getUserId();
        task.createdAt = previous == null ? now : previous.value.createdAt;
        task.updatedAt = now;
        task.revision = previous == null ? 1L : previous.value.revision + 1;
        task.name = entry.definition.systemTask ? previous.value.name : plan.name.trim();
        task.type = entry.definition.type;
        task.enabled = plan.enabled;
        task.executionActions = authority.actions(actor, entry.definition);
        task.parameters = entry.provider.prepare(task.type, plan.parameters.deepCopy(), actor.restrictActions(task.executionActions));
        if (task.parameters == null || task.parameters.toString().length() > 32768) throw new IllegalArgumentException("automation.invalidPlan");
        task.schedule = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(plan.schedule), AutomationModels.Schedule.class);
        task.nextRunAt = next;
        task.lastStatus = previous == null || java.util.Set.of("needs_attention", "uncertain").contains(
                java.util.Objects.toString(previous.value.lastStatus, "")) ? null : previous.value.lastStatus;
        task.lastRunAt = previous == null ? null : previous.value.lastRunAt;
        actor = authority.current(actor);
        if (previous == null) store.insertTask(task, actor);
        else if (!store.replaceTask(previous, task, actor)) throw new IllegalStateException("automation.taskChanged");
        return task;
    }

    public StoredTask setEnabled(AccountAccess actor, String id, long revision, boolean enabled) throws Exception {
        actor = authority.current(actor);
        AutomationStore.Snapshot<StoredTask> row = owned(actor, id);
        if (row.value.revision != revision) throw new IllegalStateException("automation.taskChanged");
        StoredTask task = row.copy(StoredTask.class);
        if (enabled) {
            if ("needs_attention".equals(task.lastStatus) || "uncertain".equals(task.lastStatus)) {
                throw new IllegalStateException("automation.reviewRequired");
            }
            TaskRegistry.Entry entry = registry.require(task.type);
            if (!entry.definition.available) throw new IllegalArgumentException("automation.taskUnavailable");
            task.executionActions = authority.actions(actor, entry.definition);
            task.parameters = entry.provider.prepare(task.type, task.parameters.deepCopy(), actor.restrictActions(task.executionActions));
            task.nextRunAt = TaskSchedule.nextAfter(task.schedule, clock.getAsLong() - 1);
            if (task.nextRunAt == null) throw new IllegalArgumentException("automation.timePassed");
        }
        task.enabled = enabled;
        task.updatedBy = actor.getUserId();
        task.revision++;
        task.updatedAt = clock.getAsLong();
        if (!store.replaceTask(row, task, actor)) throw new IllegalStateException("automation.taskChanged");
        return task;
    }

    public StoredTask delete(AccountAccess actor, String id, long revision) throws SQLException {
        actor = authority.current(actor);
        AutomationStore.Snapshot<StoredTask> row = owned(actor, id);
        if (registry.definitions().stream().anyMatch(definition -> definition.systemTask && definition.type.equals(row.value.type))) {
            throw new IllegalArgumentException("automation.systemTaskManaged");
        }
        if (row.value.activeRun != null || row.value.revision != revision) throw new IllegalStateException("automation.taskChanged");
        if (!store.deleteTask(row, actor)) throw new IllegalStateException("automation.taskChanged");
        return row.value;
    }

    public List<AutomationModels.Run> runs(AccountAccess actor, String taskId, int limit, int offset) throws SQLException {
        actor = authority.current(actor);
        // Owner prefix also protects historical runs after their task has been deleted.
        return store.visibleRuns(actor.getUserId(), systemTaskIds(actor), taskId, limit, offset).stream().map(row -> row.value).collect(Collectors.toList());
    }

    public AutomationModels.Run run(AccountAccess actor, String id) throws SQLException {
        actor = authority.current(actor);
        AutomationStore.Snapshot<AutomationModels.Run> row = store.run(id);
        if (row == null || (row.value.ownerId != actor.getUserId() && !systemTaskIds(actor).contains(row.value.taskId))) throw new PermissionErrorException();
        if (registry.definitions().stream().anyMatch(definition -> definition.systemTask && definition.type.equals(row.value.taskType))
                && !systemTaskIds(actor).contains(row.value.taskId)) throw new PermissionErrorException();
        return row.value;
    }

    AutomationStore.Snapshot<StoredTask> owned(AccountAccess actor, String id) throws SQLException {
        AutomationStore.Snapshot<StoredTask> row = store.task(id);
        if (row == null) throw new PermissionErrorException();
        authority.access(actor, row.value, registry.require(row.value.type).definition);
        return row;
    }

    public List<StoredTask> systemTasks(AccountAccess actor) throws SQLException {
        actor = authority.current(actor);
        List<StoredTask> result = new java.util.ArrayList<>();
        for (var definition : definitions(actor)) {
            if (!definition.systemTask) continue;
            var row = store.task(AutomationDefaults.systemId(definition.type));
            if (row != null) result.add(row.value);
        }
        return result;
    }

    List<String> systemTaskIds(AccountAccess actor) throws SQLException {
        List<String> result = new java.util.ArrayList<>();
        for (var task : systemTasks(actor)) {
            result.add(task.id);
        }
        return result;
    }

    public long runCount(AccountAccess actor, String taskId) throws SQLException {
        actor = authority.current(actor);
        return store.visibleRunCount(actor.getUserId(), systemTaskIds(actor), taskId);
    }
}
