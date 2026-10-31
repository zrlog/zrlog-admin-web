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
    static final String KEY = "assistant.v1.upgrade";
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
        definition.titleKey = "admin.assistant.upgrade.title";
        definition.descriptionKey = "admin.assistant.upgrade.description";
        definition.category = "maintenance";
        definition.systemTask = true;
        definition.enabledByDefault = false;
        definition.requiredActions = List.of(AccountAction.SYSTEM_MANAGE.getId());
        definition.timeoutSeconds = 600;
        definition.available = operations.available();
        definition.unavailableReasonKey = "admin.assistant.upgrade.status.unsupported";
        var retry = new AutomationModels.Field();
        retry.name = "retryUnconfirmed";
        retry.type = "boolean";
        retry.requiresConfirmation = true;
        retry.labelKey = "admin.assistant.upgrade.retry";
        var channel = new AutomationModels.Field();
        channel.name = "channel";
        channel.type = "text";
        channel.labelKey = "admin.assistant.upgrade.channel";
        channel.required = true;
        channel.readOnly = true;
        // Retrying a particular attempt is not part of enabling the recurring policy.
        definition.fields = retryAvailable() ? List.of(channel, retry) : List.of(channel);
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
                if (receipt == null || !canRetry(receipt.value)) {
                    throw new IllegalArgumentException("automation.upgrade.retryUnavailable");
                }
                result.addProperty("retryReceiptId", receipt.value.runId);
            }
        }
        return result;
    }

    @Override
    public boolean awaitingCompletion(String type, JsonObject parameters) throws SQLException {
        var receipt = store.read(KEY, Receipt.class);
        return receipt != null && "assistant".equals(receipt.value.source)
                && !"confirmed".equals(receipt.value.state) && installed(receipt.value);
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        TaskAuthority.require(context.actor(), AccountAction.SYSTEM_MANAGE.getId());
        if (!operations.available()) return attention("unsupported");
        if (!Objects.equals(parameters.get("channel").getAsString(), operations.channel())) return attention("channelChanged");
        var before = store.read(KEY, Receipt.class);
        if (before != null && !"confirmed".equals(before.value.state)
                && !("manual".equals(before.value.source) && installed(before.value))) {
            Receipt previous = before.value;
            if (installed(previous)) {
                return UpgradeResourceCompletion.result(previous, false);
            }
            boolean retry = parameters.has("retryReceiptId")
                    && previous.runId.equals(parameters.get("retryReceiptId").getAsString())
                    && canRetry(previous);
            if (!retry) {
                return clock.getAsLong() - previous.startedAt > 1800000 ? attention("unconfirmed") : submitted(previous);
            }
        }
        PreCheckVersionResponse check;
        try { check = operations.check(); }
        catch (RuntimeException error) {
            var failed = AutomationModels.Result.success("admin.assistant.version.error.failed");
            failed.status = "failed";
            return failed;
        }
        context.checkActive();
        if (!Objects.equals(parameters.get("channel").getAsString(), operations.channel())) return attention("channelChanged");
        if (!Boolean.TRUE.equals(check.getUpgrade())) return AutomationModels.Result.success("admin.assistant.version.status.current");
        if (!Boolean.TRUE.equals(check.getOnlineUpgradable())) return attention("unsupported");
        if (check.getBackupProtection() != null && Boolean.TRUE.equals(check.getBackupProtection().getRequiresRiskAcceptance())) {
            return attention("backupReview");
        }
        Version version = check.getVersion();
        if (version == null || version.getBuildId() == null || version.getBuildId().isBlank()) return attention("targetUnknown");
        if (version.getBuildId().equals(operations.currentBuildId()))
            return AutomationModels.Result.success("admin.assistant.version.status.current");
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
        return receipt != null && receipt.value.runId.equals(runId) && installed(receipt.value)
                && "confirmed".equals(receipt.value.state) ? UpgradeResourceCompletion.result(receipt.value, true) : null;
    }

    private boolean retryAvailable() {
        try {
            var receipt = store.read(KEY, Receipt.class);
            return receipt != null && canRetry(receipt.value);
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to read automatic upgrade retry state", error);
        }
    }

    private boolean canRetry(Receipt receipt) throws SQLException {
        if (!"assistant".equals(receipt.source) || installed(receipt) || "confirmed".equals(receipt.state)) return false;
        var run = store.run(receipt.runId);
        return run != null && run.value.finishedAt != null
                && (List.of("uncertain", "needs_attention").contains(run.value.status)
                || ("submitted".equals(run.value.status) && clock.getAsLong() - receipt.startedAt > 1800000));
    }

    private boolean installed(Receipt receipt) {
        return receipt.buildId != null && !receipt.buildId.isBlank() && receipt.buildId.equals(operations.currentBuildId());
    }

    /** Complete the original execution independently of its next schedule or enabled state. */
    public void completeInstalled(TaskAuthority authority, long budgetMillis) throws SQLException {
        new UpgradeResourceCompletion(store, authority, operations, clock).complete(budgetMillis);
    }

    private static AutomationModels.Result submitted(Receipt receipt) {
        var result = AutomationModels.Result.success("admin.assistant.upgrade.status.submitted");
        result.status = "submitted";
        result.text = receipt.version;
        result.externalRunId = receipt.runId;
        result.actionPath = "/upgrade";
        return result;
    }

    private static AutomationModels.Result attention(String reason) {
        var result = AutomationModels.Result.attention("admin.assistant.upgrade.status." + reason);
        result.actionPath = "/upgrade";
        return result;
    }

    public interface Operations {
        boolean available();
        String channel();
        String currentBuildId();
        PreCheckVersionResponse check();
        boolean submit(Version target, TaskExecutionContext context) throws Exception;
        void synchronizeResources(TaskExecutionContext context) throws Exception;
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
        public String completionId;
        public long completionLeaseUntil;
        public long nextCompletionAt;
        public String resourceStatus;
        public Long resourceStartedAt;
        public Long resourceFinishedAt;
    }
}
