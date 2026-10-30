package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.data.security.AccountAccess;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Request-bounded execution, shared by external ticks, resident scheduling and manual runs. */
public final class AutomationEngine {
    private static final Logger LOGGER = Logger.getLogger(AutomationEngine.class.getName());
    private static final long RESULT_RESERVE_MILLIS = 5000;
    private static final Set<String> RESULTS = Set.of("success", "submitted", "failed", "needs_attention", "uncertain");
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final TaskRegistry registry;
    private final LongSupplier clock;

    public AutomationEngine(AutomationStore store, TaskAuthority authority, TaskRegistry registry, LongSupplier clock) {
        this.store = store;
        this.authority = authority;
        this.registry = registry;
        this.clock = clock;
    }

    public AutomationModels.TickResult tick(String trigger, long budgetMillis) throws SQLException {
        if (!Set.of("internal", "external").contains(trigger) || budgetMillis < 1 || budgetMillis > 660000) {
            throw new IllegalArgumentException("automation.invalidTrigger");
        }
        long deadline = Math.addExact(clock.getAsLong(), budgetMillis);
        AutomationModels.TickResult summary = new AutomationModels.TickResult();
        for (AutomationStore.Snapshot<StoredTask> row : store.due(clock.getAsLong(), 32)) {
            if (clock.getAsLong() >= deadline) {
                summary.budgetExhausted = true;
                break;
            }
            if (row.value.activeRun != null) {
                recover(row);
                summary.skipped++;
                continue;
            }
            TaskRegistry.Entry entry = null;
            try { entry = registry.require(row.value.type); }
            catch (IllegalArgumentException ignored) { /* Missing provider is a visible, durable result. */ }
            long requiredMillis = entry == null ? 0 : entry.definition.timeoutSeconds * 1000L + RESULT_RESERVE_MILLIS;
            if (entry != null && requiredMillis > deadline - clock.getAsLong()) {
                summary.budgetExhausted = true;
                if (requiredMillis > budgetMillis) {
                    summary.insufficientBudgetTaskIds.add(row.value.id);
                    summary.requiredBudgetSeconds = Math.max(summary.requiredBudgetSeconds, (int) (requiredMillis / 1000));
                }
                summary.skipped++;
                continue;
            }
            AutomationModels.Run run = execute(row, entry, trigger, deadline - RESULT_RESERVE_MILLIS, null);
            if (run == null) { summary.skipped++; continue; }
            summary.executed++;
            summary.runIds.add(run.id);
            if ("failed".equals(run.status) || "uncertain".equals(run.status)) summary.failed++;
            if ("needs_attention".equals(run.status) || "uncertain".equals(run.status)) summary.needsAttention++;
        }
        return summary;
    }

    public AutomationModels.Run runNow(AccountAccess actor, String id, long revision, long budgetMillis) throws SQLException {
        actor = authority.current(actor);
        AutomationStore.Snapshot<StoredTask> row = store.task(id);
        if (row == null) throw new PermissionErrorException();
        authority.access(actor, row.value, registry.require(row.value.type).definition);
        if (row.value.revision != revision || row.value.activeRun != null) throw new IllegalStateException("automation.taskChanged");
        if ("uncertain".equals(row.value.lastStatus) || "needs_attention".equals(row.value.lastStatus)) {
            throw new IllegalStateException("automation.reviewRequired");
        }
        TaskRegistry.Entry entry = registry.require(row.value.type);
        if (budgetMillis < entry.definition.timeoutSeconds * 1000L + RESULT_RESERVE_MILLIS || budgetMillis > 660000) {
            throw new IllegalArgumentException("automation.insufficientBudget");
        }
        // An API delegate must not use the owner's stored grants to broaden this manual request.
        for (String action : row.value.executionActions) TaskAuthority.require(actor, action);
        AutomationModels.Run run = execute(row, entry, "manual", clock.getAsLong() + budgetMillis - RESULT_RESERVE_MILLIS, actor);
        if (run == null) throw new IllegalStateException("automation.taskChanged");
        return run;
    }

    private AutomationModels.Run execute(AutomationStore.Snapshot<StoredTask> row, TaskRegistry.Entry entry,
                                          String trigger, long requestDeadline, AccountAccess manualActor) throws SQLException {
        StoredTask task = row.copy(StoredTask.class);
        if (task.activeRun != null || (!"manual".equals(trigger) && (!task.enabled || task.nextRunAt == null
                || task.nextRunAt > clock.getAsLong()))) return null;
        AccountAccess actor = null;
        String unavailable = null;
        try {
            actor = manualActor == null ? authority.restore(task) : manualActor.restrictActions(task.executionActions);
            if (entry == null || !entry.definition.available) unavailable = "automation.taskUnavailable";
            else for (String action : entry.definition.requiredActions) TaskAuthority.require(actor, action);
        } catch (PermissionErrorException error) {
            unavailable = "automation.permissionChanged";
        }
        long now = clock.getAsLong();
        AutomationModels.Run run = new AutomationModels.Run();
        run.id = AutomationStore.ownerPrefix(task.ownerId) + AutomationStore.timeKey(now) + "." + UUID.randomUUID();
        run.taskId = task.id;
        run.ownerId = task.ownerId;
        run.taskName = task.name;
        run.taskType = task.type;
        run.taskRevision = task.revision;
        run.planSnapshot = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(task), AutomationModels.Plan.class);
        run.scheduledAt = "manual".equals(trigger) || task.nextRunAt == null ? now : task.nextRunAt;
        run.startedAt = now;
        run.trigger = trigger;
        run.triggeredBy = manualActor == null ? null : manualActor.getUserId();
        run.executionUserId = actor == null ? null : actor.getUserId();
        run.status = "running";
        task.activeRun = run;
        task.leaseUntil = Math.min(requestDeadline, now + (entry == null ? 30000 : entry.definition.timeoutSeconds * 1000L)) + 5000;
        // The claim includes a recovery copy of the run before a separate run-row insert.
        if (!store.replaceTask(row, task, unavailable == null ? actor : null)) return null;
        store.insertRun(run);
        if (unavailable != null) {
            run.result = AutomationModels.Result.attention(unavailable);
            run.status = run.result.status;
        } else {
            final AccountAccess executionActor = actor;
            TaskExecutionContext context = new TaskExecutionContext(run.id, executionActor,
                    Math.min(requestDeadline, task.leaseUntil - 5000), clock, () -> checkOwnership(task, executionActor), progress -> {
                var checkpoint = store.run(run.id);
                if (checkpoint == null || !"running".equals(checkpoint.value.status)) throw new IllegalStateException("automation.runChanged");
                var next = checkpoint.copy(AutomationModels.Run.class);
                next.result = progress;
                if (!store.replaceRun(checkpoint, next)) throw new IllegalStateException("automation.runChanged");
            });
            try {
                context.checkActive();
                AutomationModels.Result result = entry.provider.execute(task.type, task.parameters.deepCopy(), context);
                context.checkActive();
                if (result == null || !RESULTS.contains(result.status)) throw new IllegalStateException("automation.invalidResult");
                if (result.text != null && result.text.length() > 64000) result.text = result.text.substring(0, 64000);
                run.result = result;
                run.status = result.status;
            } catch (PermissionErrorException error) {
                run.status = "needs_attention";
                run.errorCode = "automation.permissionChanged";
            } catch (Exception error) {
                LOGGER.log(Level.WARNING,
                        "Assistant task execution failed: type=" + task.type + ", run=" + run.id, error);
                // A thrown exception cannot prove that an external side effect did not happen.
                run.status = "uncertain";
                run.errorCode = "automation.executionUncertain";
            }
        }
        run.finishedAt = clock.getAsLong();
        AutomationStore.Snapshot<AutomationModels.Run> previous = store.run(run.id);
        if (run.result == null && previous != null) {
            run.result = previous.value.result;
            if (run.result != null) markUnfinished(run.result.children);
        }
        if (previous == null || !"running".equals(previous.value.status) || !store.replaceRun(previous, run)) {
            throw new IllegalStateException("automation.runChanged");
        }
        release(task.id, run);
        try { store.pruneRuns(task.ownerId, clock.getAsLong()); }
        catch (SQLException error) {
            LOGGER.log(Level.WARNING, "Assistant history retention failed", error);
        }
        return run;
    }

    private void checkOwnership(StoredTask expected, AccountAccess executionActor) throws Exception {
        AutomationStore.Snapshot<StoredTask> row = store.task(expected.id);
        if (row == null || row.value.activeRun == null || !row.value.activeRun.id.equals(expected.activeRun.id)
                || row.value.leaseUntil <= clock.getAsLong()) throw new IllegalStateException("automation.executionExpired");
        authority.revalidateExecution(executionActor);
    }

    private void recover(AutomationStore.Snapshot<StoredTask> row) throws SQLException {
        if (row.value.leaseUntil > clock.getAsLong()) return;
        AutomationModels.Run run = row.value.activeRun;
        AutomationStore.Snapshot<AutomationModels.Run> storedRun = store.run(run.id);
        if (storedRun != null && !"running".equals(storedRun.value.status)) {
            release(row.value.id, storedRun.value);
            return;
        }
        if (storedRun != null) run = storedRun.value;
        if (run.result != null) markUnfinished(run.result.children);
        run.status = "uncertain";
        run.finishedAt = clock.getAsLong();
        run.errorCode = "automation.executionInterrupted";
        try {
            TaskRegistry.Entry entry = registry.require(row.value.type);
            AutomationModels.Result recovered = entry.provider.recover(row.value.type, row.value.parameters.deepCopy(),
                    run.id, authority.restore(row.value));
            if (recovered != null && "success".equals(recovered.status)) {
                run.result = recovered;
                run.status = "success";
                run.errorCode = null;
            }
        } catch (Exception ignored) { /* No proof: preserve the interrupted outcome and require review. */ }
        if (storedRun == null) {
            try { store.insertRun(run); }
            catch (SQLException error) {
                storedRun = store.run(run.id);
                if (storedRun == null) throw error;
                if ("running".equals(storedRun.value.status)) {
                    if (!store.replaceRun(storedRun, run)) return;
                } else run = storedRun.value;
            }
        } else if (!store.replaceRun(storedRun, run)) {
            storedRun = store.run(run.id);
            if (storedRun == null || "running".equals(storedRun.value.status)) return;
            run = storedRun.value;
        }
        release(row.value.id, run);
    }

    private static void markUnfinished(java.util.List<AutomationModels.ChildResult> children) {
        if (children == null) return;
        for (var child : children) {
            if ("running".equals(child.status)) child.status = "uncertain";
            markUnfinished(child.children);
        }
    }

    private void release(String id, AutomationModels.Run run) throws SQLException {
        for (int attempt = 0; attempt < 3; attempt++) {
            AutomationStore.Snapshot<StoredTask> row = store.task(id);
            if (row == null || row.value.activeRun == null || !Objects.equals(row.value.activeRun.id, run.id)) return;
            StoredTask task = row.copy(StoredTask.class);
            task.activeRun = null;
            task.leaseUntil = 0;
            task.lastStatus = run.status;
            task.lastRunAt = run.startedAt;
            task.updatedAt = clock.getAsLong();
            if (Set.of("uncertain", "needs_attention").contains(run.status)) task.enabled = false;
            else if (!"manual".equals(run.trigger) || "once".equals(task.schedule.kind)
                    || task.nextRunAt == null || task.nextRunAt <= run.startedAt) {
                task.nextRunAt = TaskSchedule.nextAfter(task.schedule, Math.max(run.scheduledAt, clock.getAsLong()));
                if (task.nextRunAt == null) task.enabled = false;
            }
            if (store.replaceTask(row, task, null)) return;
        }
        throw new IllegalStateException("automation.taskChanged");
    }
}
