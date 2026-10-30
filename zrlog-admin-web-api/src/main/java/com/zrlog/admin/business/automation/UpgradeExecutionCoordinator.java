package com.zrlog.admin.business.automation;

import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.BlogBuildInfoUtil;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Manual and scheduled upgrades claim the same durable application-wide receipt before effects. */
public final class UpgradeExecutionCoordinator {
    private static final String RUNTIME_ID = UUID.randomUUID().toString();
    private static final long REVIEW_AFTER_MILLIS = 30 * 60 * 1000L;
    private final AutomationStore store;
    private final TaskAuthority authority;
    private final LongSupplier clock;
    private final Supplier<String> currentBuild;

    @Inject
    public UpgradeExecutionCoordinator(AutomationStore store, TaskAuthority authority) {
        this(store, authority, System::currentTimeMillis, BlogBuildInfoUtil::getBuildId);
    }

    UpgradeExecutionCoordinator(AutomationStore store, TaskAuthority authority, LongSupplier clock, Supplier<String> currentBuild) {
        this.store = store; this.authority = authority; this.clock = clock; this.currentBuild = currentBuild;
    }

    public View view(AccountAccess actor) throws SQLException {
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        var row = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        return row == null ? null : view(row.value, actor);
    }

    public AutomaticUpgradeTaskProvider.Receipt claimManual(AccountAccess actor, Version target,
                                                              String dockerRequestId, String channel, String reviewedExecutionId) throws SQLException {
        actor = authority.current(actor);
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        if (target == null || target.getBuildId() == null || target.getBuildId().isBlank())
            throw new IllegalArgumentException("automation.upgrade.targetUnknown");
        var before = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
        if (before != null && !installed(before.value) && !"confirmed".equals(before.value.state)) {
            var previous = before.value;
            // Docker owns idempotency for resubmitting exactly the same target and request identity.
            if (dockerRequestId != null && "manual".equals(previous.source)
                    && dockerRequestId.equals(previous.externalRequestId)
                    && Objects.equals(target.getBuildId(), previous.buildId)
                    && Objects.equals(target.getVersion(), previous.version)
                    && Objects.equals(channel, previous.channel)) return previous;
            if (!Objects.equals(previous.runId, reviewedExecutionId) || !canRetry(previous, actor))
                throw new Conflict(view(previous, actor));
        } else if (reviewedExecutionId != null) {
            // A review of an old operation never authorizes replacing a different, newer receipt.
            if (before == null || !Objects.equals(before.value.runId, reviewedExecutionId))
                throw new Conflict(before == null ? null : view(before.value, actor));
        }
        var receipt = new AutomaticUpgradeTaskProvider.Receipt();
        receipt.runId = AutomationStore.ownerPrefix(actor.getUserId()) + AutomationStore.timeKey(clock.getAsLong()) + "." + UUID.randomUUID();
        receipt.ownerId = actor.getUserId();
        receipt.buildId = target.getBuildId(); receipt.version = target.getVersion();
        receipt.source = "manual"; receipt.state = "pending"; receipt.phase = "running";
        receipt.externalRequestId = dockerRequestId; receipt.startedAt = clock.getAsLong();
        receipt.channel = channel; receipt.runtimeId = RUNTIME_ID;
        if (!store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, before, receipt, actor)) throw new Conflict(view(actor));
        return receipt;
    }

    /** A return from submission is not proof that the target build is installed. */
    public void finish(AutomaticUpgradeTaskProvider.Receipt receipt, boolean submitted) throws SQLException {
        for (int attempt = 0; attempt < 3; attempt++) {
            var current = store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class);
            if (current == null || !Objects.equals(receipt.runId, current.value.runId)) return;
            var next = current.copy(AutomaticUpgradeTaskProvider.Receipt.class);
            next.phase = submitted ? "submitted" : "uncertain";
            next.finishedAt = clock.getAsLong();
            if (store.write(AutomaticUpgradeTaskProvider.KEY, current, next)) return;
        }
        throw new IllegalStateException("automation.upgrade.unconfirmed");
    }

    private View view(AutomaticUpgradeTaskProvider.Receipt receipt, AccountAccess actor) throws SQLException {
        var view = new View();
        view.id = receipt.runId; view.source = Objects.toString(receipt.source, "assistant");
        view.version = receipt.version; view.buildId = receipt.buildId; view.startedAt = receipt.startedAt;
        view.confirmed = "confirmed".equals(receipt.state) || installed(receipt);
        view.canRetry = !view.confirmed && canRetry(receipt, actor);
        view.state = view.confirmed ? "confirmed" : view.canRetry ? "needs_review" : "pending";
        return view;
    }

    private boolean canRetry(AutomaticUpgradeTaskProvider.Receipt receipt, AccountAccess actor) throws SQLException {
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        if (installed(receipt) || "confirmed".equals(receipt.state)) return false;
        if ("manual".equals(receipt.source)) {
            return "uncertain".equals(receipt.phase)
                    || clock.getAsLong() - receipt.startedAt > REVIEW_AFTER_MILLIS
                    && ("submitted".equals(receipt.phase) || !Objects.equals(receipt.runtimeId, RUNTIME_ID));
        }
        var run = store.run(receipt.runId);
        return run != null && run.value.finishedAt != null
                && (List.of("uncertain", "needs_attention").contains(run.value.status)
                || "submitted".equals(run.value.status) && clock.getAsLong() - receipt.startedAt > REVIEW_AFTER_MILLIS);
    }

    private boolean installed(AutomaticUpgradeTaskProvider.Receipt receipt) {
        return receipt.buildId != null && !receipt.buildId.isBlank() && receipt.buildId.equals(currentBuild.get());
    }

    public static final class View {
        public String id, source, version, buildId, state;
        public long startedAt;
        public boolean confirmed, canRetry;
    }

    public static final class Conflict extends IllegalStateException {
        public final View execution;
        Conflict(View execution) { super("automation.upgrade.pending"); this.execution = execution; }
    }
}
