package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.data.security.AccountAccess;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Personal task management; task enablement never grants permissions or starts an execution. */
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

    public List<StoredTask> tasks(AccountAccess actor, int offset) throws SQLException {
        actor = authority.current(actor);
        return store.tasks(actor.getUserId(), 100, offset).stream().map(row -> row.value).collect(Collectors.toList());
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
        AutomationStore.Snapshot<StoredTask> previous = plan.id == null ? null : owned(actor, plan.id);
        if (previous != null && (!Objects.equals(previous.value.revision, plan.revision) || previous.value.activeRun != null)) {
            throw new IllegalStateException("automation.taskChanged");
        }
        long now = clock.getAsLong();
        Long next = TaskSchedule.nextAfter(plan.schedule, now - 1);
        if (next == null && plan.enabled) throw new IllegalArgumentException("automation.timePassed");
        StoredTask task = new StoredTask();
        task.id = previous == null ? AutomationStore.ownerPrefix(actor.getUserId()) + UUID.randomUUID() : previous.value.id;
        task.ownerId = actor.getUserId();
        task.ownerAuthVersion = actor.getAuthVersion();
        task.ownerRole = actor.getRole();
        task.createdAt = previous == null ? now : previous.value.createdAt;
        task.updatedAt = now;
        task.revision = previous == null ? 1L : previous.value.revision + 1;
        task.name = plan.name.trim();
        task.type = entry.definition.type;
        task.enabled = plan.enabled;
        task.authorizedActions = authority.grants(actor, entry.definition, plan.authorizedActions);
        task.parameters = entry.provider.prepare(task.type, plan.parameters.deepCopy(), actor.restrictActions(task.authorizedActions));
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
            authority.restore(task);
            TaskRegistry.Entry entry = registry.require(task.type);
            if (!entry.definition.available) throw new IllegalArgumentException("automation.taskUnavailable");
            task.nextRunAt = TaskSchedule.nextAfter(task.schedule, clock.getAsLong() - 1);
            if (task.nextRunAt == null) throw new IllegalArgumentException("automation.timePassed");
        }
        task.enabled = enabled;
        task.revision++;
        task.updatedAt = clock.getAsLong();
        if (!store.replaceTask(row, task, actor)) throw new IllegalStateException("automation.taskChanged");
        return task;
    }

    public void delete(AccountAccess actor, String id, long revision) throws SQLException {
        actor = authority.current(actor);
        AutomationStore.Snapshot<StoredTask> row = owned(actor, id);
        if (row.value.activeRun != null || row.value.revision != revision) throw new IllegalStateException("automation.taskChanged");
        if (!store.deleteTask(row, actor)) throw new IllegalStateException("automation.taskChanged");
    }

    public List<AutomationModels.Run> runs(AccountAccess actor, String taskId, int limit, int offset) throws SQLException {
        actor = authority.current(actor);
        // Owner prefix also protects historical runs after their task has been deleted.
        return store.runs(actor.getUserId(), taskId, limit, offset).stream().map(row -> row.value).collect(Collectors.toList());
    }

    public AutomationModels.Run run(AccountAccess actor, String id) throws SQLException {
        actor = authority.current(actor);
        AutomationStore.Snapshot<AutomationModels.Run> row = store.run(id);
        if (row == null || row.value.ownerId != actor.getUserId()) throw new PermissionErrorException();
        return row.value;
    }

    AutomationStore.Snapshot<StoredTask> owned(AccountAccess actor, String id) throws SQLException {
        AutomationStore.Snapshot<StoredTask> row = store.task(id);
        if (row == null || row.value.ownerId != actor.getUserId()) throw new PermissionErrorException();
        return row;
    }
}
