package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Submits a confirmed upgrade policy and records the exact target before touching the application. */
public final class AutomaticUpgradeTaskProvider implements ScheduledTaskProvider {
    static final String KEY = "assistant.upgrade";
    private final AutomationStore store;
    private final Operations operations;
    private final LongSupplier clock;

    public AutomaticUpgradeTaskProvider(AutomationStore store, Operations operations, LongSupplier clock) {
        this.store = store;
        this.operations = operations;
        this.clock = clock;
    }

    @Override
    public List<AutomationModels.Definition> definitions() {
        var definition = new AutomationModels.Definition();
        definition.type = "system.auto-upgrade";
        definition.titleKey = "admin.automation.upgrade.title";
        definition.descriptionKey = "admin.automation.upgrade.description";
        definition.category = "maintenance";
        definition.requiredActions = List.of(AccountAction.SYSTEM_MANAGE.getId());
        definition.requiresAuthorization = true;
        definition.timeoutSeconds = 600;
        definition.available = operations.available();
        definition.unavailableReasonKey = "admin.automation.upgrade.unsupported";
        var retry = new AutomationModels.Field();
        retry.name = "retryUnconfirmed";
        retry.type = "boolean";
        retry.requiresConfirmation = true;
        retry.labelKey = "admin.automation.upgrade.retry";
        var channel = new AutomationModels.Field();
        channel.name = "channel";
        channel.type = "text";
        channel.labelKey = "admin.automation.upgrade.channel";
        channel.required = true;
        channel.readOnly = true;
        definition.fields = List.of(channel, retry);
        definition.defaultParameters.addProperty("channel", operations.channel());
        definition.defaultSchedule = new AutomationModels.Schedule();
        definition.defaultSchedule.kind = "daily";
        definition.defaultSchedule.time = "03:00";
        return List.of(definition);
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) throws SQLException {
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        if (!operations.available()) throw new IllegalArgumentException("automation.taskUnavailable");
        if (!parameters.has("channel") || !parameters.get("channel").isJsonPrimitive()
                || !parameters.getAsJsonPrimitive("channel").isString()
                || !operations.channel().equals(parameters.get("channel").getAsString())) {
            throw new IllegalArgumentException("automation.upgrade.channelChanged");
        }
        JsonObject result = new JsonObject();
        result.addProperty("channel", parameters.get("channel").getAsString());
        if (parameters.has("retryUnconfirmed")) {
            if (!parameters.get("retryUnconfirmed").isJsonPrimitive()
                    || !parameters.getAsJsonPrimitive("retryUnconfirmed").isBoolean()) {
                throw new IllegalArgumentException("automation.invalidParameters");
            }
            if (parameters.get("retryUnconfirmed").getAsBoolean()) {
                var receipt = store.read(KEY, Receipt.class);
                if (receipt == null || !canRetry(receipt.value, actor)) {
                    throw new IllegalArgumentException("automation.upgrade.retryUnavailable");
                }
                result.addProperty("retryReceiptId", receipt.value.runId);
            }
        }
        return result;
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        TaskAuthority.require(context.actor(), AccountAction.SYSTEM_MANAGE.getId());
        if (!operations.available()) return attention("unsupported");
        if (!Objects.equals(parameters.get("channel").getAsString(), operations.channel())) return attention("channelChanged");
        var before = store.read(KEY, Receipt.class);
        if (before != null && !"confirmed".equals(before.value.state)) {
            Receipt previous = before.value;
            if (installed(previous)) {
                Receipt confirmed = before.copy(Receipt.class);
                confirmed.state = "confirmed";
                if (!store.writeAuthorized(KEY, before, confirmed, context.actor())) return attention("pending");
                confirmRun(previous);
                // Confirm the prior submission first; the next cycle can check for another release.
                var result = confirmed(previous);
                // The original submission owns the notification; the confirming tick is quiet.
                result.notifyOwner = false;
                return result;
            }
            boolean retry = parameters.has("retryReceiptId")
                    && previous.runId.equals(parameters.get("retryReceiptId").getAsString())
                    && canRetry(previous, context.actor());
            if (!retry) {
                return clock.getAsLong() - previous.startedAt > 1800000 ? attention("unconfirmed") : submitted(previous);
            }
        }
        PreCheckVersionResponse check;
        try { check = operations.check(); }
        catch (RuntimeException error) {
            var failed = AutomationModels.Result.success("admin.automation.version.failed");
            failed.status = "failed";
            return failed;
        }
        context.checkActive();
        if (!Objects.equals(parameters.get("channel").getAsString(), operations.channel())) return attention("channelChanged");
        if (!Boolean.TRUE.equals(check.getUpgrade())) return AutomationModels.Result.success("admin.automation.version.current");
        if (!Boolean.TRUE.equals(check.getOnlineUpgradable())) return attention("unsupported");
        if (check.getBackupProtection() != null && Boolean.TRUE.equals(check.getBackupProtection().getRequiresRiskAcceptance())) {
            return attention("backupReview");
        }
        Version version = check.getVersion();
        if (version == null || version.getBuildId() == null || version.getBuildId().isBlank()) return attention("targetUnknown");
        Receipt receipt = new Receipt();
        receipt.runId = context.runId();
        receipt.ownerId = context.actor().getUserId();
        receipt.buildId = version.getBuildId();
        receipt.version = version.getVersion();
        receipt.startedAt = clock.getAsLong();
        receipt.state = "pending";
        receipt.source = "assistant";
        // One application-wide claim prevents separate authorized tasks from submitting two upgrades.
        if (!store.writeAuthorized(KEY, before, receipt, context.actor())) return attention("pending");
        context.checkActive();
        boolean accepted = operations.submit(version, context);
        return accepted ? submitted(receipt) : attention("unconfirmed");
    }

    @Override
    public AutomationModels.Result recover(String type, JsonObject parameters, String runId, AccountAccess actor) throws SQLException {
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        var receipt = store.read(KEY, Receipt.class);
        return receipt != null && receipt.value.runId.equals(runId) && installed(receipt.value) ? confirmed(receipt.value) : null;
    }

    private boolean canRetry(Receipt receipt, AccountAccess actor) throws SQLException {
        if (receipt.ownerId != actor.getUserId() || installed(receipt) || "confirmed".equals(receipt.state)) return false;
        var run = store.run(receipt.runId);
        return run != null && run.value.finishedAt != null
                && (List.of("uncertain", "needs_attention").contains(run.value.status)
                || ("submitted".equals(run.value.status) && clock.getAsLong() - receipt.startedAt > 1800000));
    }

    private boolean installed(Receipt receipt) {
        return receipt.buildId != null && !receipt.buildId.isBlank() && receipt.buildId.equals(operations.currentBuildId());
    }

    private void confirmRun(Receipt receipt) throws SQLException {
        var run = store.run(receipt.runId);
        if (run == null || !"submitted".equals(run.value.status)) return;
        var confirmed = run.copy(AutomationModels.Run.class);
        confirmed.status = "success";
        confirmed.finishedAt = clock.getAsLong();
        confirmed.result = confirmed(receipt);
        if (store.replaceRun(run, confirmed)) {
            var task = store.task(run.value.taskId);
            if (task != null && task.value.activeRun == null && "submitted".equals(task.value.lastStatus)
                    && Objects.equals(task.value.lastRunAt, run.value.startedAt)) {
                var completed = task.copy(StoredTask.class);
                completed.lastStatus = "success";
                store.replaceTask(task, completed, null);
            }
        }
    }

    /** Local version proof also completes one-shot submissions after the application restarts. */
    public void confirmInstalled() throws SQLException {
        var receipt = store.read(KEY, Receipt.class);
        if (receipt != null && installed(receipt.value)) confirmRun(receipt.value);
    }

    private static AutomationModels.Result submitted(Receipt receipt) {
        var result = AutomationModels.Result.success("admin.automation.upgrade.submitted");
        result.status = "submitted";
        result.text = receipt.version;
        result.externalRunId = receipt.runId;
        result.actionPath = "/upgrade";
        return result;
    }

    private static AutomationModels.Result confirmed(Receipt receipt) {
        var result = AutomationModels.Result.success("admin.automation.upgrade.confirmed");
        result.notifyOwner = true;
        result.text = receipt.version;
        result.actionPath = "/upgrade";
        return result;
    }

    private static AutomationModels.Result attention(String reason) {
        var result = AutomationModels.Result.attention("admin.automation.upgrade." + reason);
        result.actionPath = "/upgrade";
        return result;
    }

    public interface Operations {
        boolean available();
        String channel();
        String currentBuildId();
        PreCheckVersionResponse check();
        boolean submit(Version target, TaskExecutionContext context) throws Exception;
    }

    public static class Receipt {
        public String runId;
        public int ownerId;
        public String buildId;
        public String version;
        public String state;
        public String source;
        public String phase;
        public String externalRequestId;
        public String channel;
        public String runtimeId;
        public Long finishedAt;
        public long startedAt;
    }
}
