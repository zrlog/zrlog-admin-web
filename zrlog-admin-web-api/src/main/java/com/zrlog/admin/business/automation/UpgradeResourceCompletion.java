package com.zrlog.admin.business.automation;

import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Resumes an installed upgrade in the current tick, including its resource delivery and original run. */
final class UpgradeResourceCompletion {
    private static final Logger LOGGER = Logger.getLogger(UpgradeResourceCompletion.class.getName());
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final AutomaticUpgradeTaskProvider.Operations operations;
    private final LongSupplier clock;

    UpgradeResourceCompletion(AutomationStore store, TaskAuthority authority,
                              AutomaticUpgradeTaskProvider.Operations operations, LongSupplier clock) {
        this.store = store;
        this.authority = authority;
        this.operations = operations;
        this.clock = clock;
    }

    void complete(long budgetMillis) throws SQLException {
        var before = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        if (before == null || !"assistant".equals(before.value.source) || !installed(before.value)) return;
        var receipt = before.value;
        if ("confirmed".equals(receipt.state)) return;
        // The delivery checkpoint survives a crash between receipt and run writes.
        if ("resources_synced".equals(receipt.phase)) {
            finish(before);
            return;
        }
        long now = clock.getAsLong();
        if (budgetMillis <= 5000 || receipt.completionLeaseUntil > now || receipt.nextCompletionAt > now) return;
        AccountAccess actor = authority.siteActor().restrictActions(List.of(AccountAction.SYSTEM_MANAGE.getId()));
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        var claimed = before.copy(AutomaticUpgradeTaskProvider.Receipt.class);
        claimed.completionId = UUID.randomUUID().toString();
        claimed.completionLeaseUntil = Math.addExact(now, Math.min(budgetMillis - 5000, 600000));
        claimed.resourceStartedAt = now;
        claimed.resourceFinishedAt = null;
        claimed.resourceStatus = "running";
        if (!store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, before, claimed, actor)) return;
        var context = new TaskExecutionContext(receipt.runId, actor, claimed.completionLeaseUntil, clock,
                () -> fence(claimed, actor));
        try {
            recordRun(claimed, false);
            context.checkActive();
            operations.synchronizeResources(context);
            context.checkActive();
            var current = owned(claimed);
            var synced = current.copy(AutomaticUpgradeTaskProvider.Receipt.class);
            synced.phase = "resources_synced";
            synced.resourceStatus = "success";
            synced.resourceFinishedAt = clock.getAsLong();
            if (!store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, current, synced, actor))
                throw new IllegalStateException("automation.executionExpired");
            finish(store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Automatic upgrade resource completion failed: run=" + receipt.runId, error);
            var current = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
            if (current == null || !Objects.equals(current.value.completionId, claimed.completionId)) return;
            // A successful delivery checkpoint must never be erased by a later bookkeeping failure.
            if ("resources_synced".equals(current.value.phase) || "confirmed".equals(current.value.state)) return;
            var failed = current.copy(AutomaticUpgradeTaskProvider.Receipt.class);
            failed.resourceStatus = "failed";
            failed.resourceFinishedAt = clock.getAsLong();
            failed.completionLeaseUntil = 0;
            failed.nextCompletionAt = clock.getAsLong() + 60000;
            if (store.write(AutomaticUpgradeTaskProvider.KEY, current, failed)) recordRun(failed, false);
        }
    }

    private boolean installed(AutomaticUpgradeTaskProvider.Receipt receipt) {
        return receipt.buildId != null && !receipt.buildId.isBlank()
                && receipt.buildId.equals(operations.currentBuildId());
    }

    private AutomationStore.Snapshot<AutomaticUpgradeTaskProvider.Receipt> owned(
            AutomaticUpgradeTaskProvider.Receipt expected) throws SQLException {
        var current = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        if (current == null || !Objects.equals(expected.runId, current.value.runId)
                || !Objects.equals(expected.completionId, current.value.completionId)
                || !installed(current.value)) throw new IllegalStateException("automation.executionExpired");
        return current;
    }

    private void fence(AutomaticUpgradeTaskProvider.Receipt expected, AccountAccess actor) throws SQLException {
        if (owned(expected).value.completionLeaseUntil <= clock.getAsLong())
            throw new IllegalStateException("automation.executionExpired");
        authority.revalidateExecution(actor);
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
    }

    private void finish(AutomationStore.Snapshot<AutomaticUpgradeTaskProvider.Receipt> current) throws SQLException {
        if (current == null || !installed(current.value) || !"resources_synced".equals(current.value.phase)) return;
        recordRun(current.value, true);
        var confirmed = current.copy(AutomaticUpgradeTaskProvider.Receipt.class);
        confirmed.state = "confirmed";
        confirmed.completionLeaseUntil = 0;
        confirmed.nextCompletionAt = 0;
        store.write(AutomaticUpgradeTaskProvider.KEY, current, confirmed);
    }

    private void recordRun(AutomaticUpgradeTaskProvider.Receipt receipt, boolean complete) throws SQLException {
        for (int attempt = 0; attempt < 3; attempt++) {
            var row = store.run(receipt.runId);
            if (row == null) throw new IllegalStateException("automation.runChanged");
            var run = row.value;
            if (!"success".equals(run.status)) {
                run = row.copy(AutomationModels.Run.class);
                run.status = complete ? "success" : "submitted";
                run.errorCode = null;
                run.finishedAt = clock.getAsLong();
                run.result = result(receipt, complete);
                if (!store.replaceRun(row, run)) continue;
            }
            // A new build proves installation even if the old process never released its task lease.
            AutomationEngine.release(store, clock, run.taskId, run);
            for (int update = 0; update < 3; update++) {
                var task = store.task(run.taskId);
                if (task == null || task.value.activeRun != null
                        || !Objects.equals(task.value.lastRunAt, run.startedAt)) break;
                var next = task.copy(StoredTask.class);
                next.lastStatus = run.status;
                if (store.replaceTask(task, next, null)) break;
            }
            return;
        }
        throw new IllegalStateException("automation.runChanged");
    }

    static AutomationModels.Result result(AutomaticUpgradeTaskProvider.Receipt receipt, boolean complete) {
        var result = AutomationModels.Result.success(complete ? "admin.automation.upgrade.confirmed"
                : "admin.automation.upgrade.resourcesPending");
        result.status = complete ? "success" : "submitted";
        result.notifyOwner = complete;
        result.text = receipt.version;
        result.externalRunId = receipt.runId;
        result.actionPath = "/upgrade";
        var installed = new AutomationModels.ChildResult();
        installed.id = "installed";
        installed.nameKey = "admin.automation.upgrade.installedStep";
        installed.status = "success";
        installed.messageKey = "admin.automation.upgrade.installed";
        installed.text = receipt.version;
        var resources = new AutomationModels.ChildResult();
        resources.id = "resources";
        resources.nameKey = "admin.automation.upgrade.resourcesStep";
        resources.status = Objects.toString(receipt.resourceStatus, "running");
        resources.messageKey = "success".equals(resources.status) ? "admin.automation.upgrade.resourcesSynced"
                : "failed".equals(resources.status) ? "admin.automation.upgrade.resourcesFailed"
                : "admin.automation.upgrade.resourcesPending";
        resources.startedAt = receipt.resourceStartedAt;
        resources.finishedAt = receipt.resourceFinishedAt;
        result.children = List.of(installed, resources);
        return result;
    }
}
