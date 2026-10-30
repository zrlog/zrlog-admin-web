package com.zrlog.admin.business.automation;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.data.security.AccountAccess;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Explicit identity, execution deadline and ownership fence; never captures a browser session. */
public final class TaskExecutionContext {
    private final String runId;
    private final AccountAccess actor;
    private final long deadline;
    private final LongSupplier clock;
    private final CheckedAction fence;
    private final Checkpoint checkpoint;

    public TaskExecutionContext(String runId, AccountAccess actor, long deadline,
                                LongSupplier clock, CheckedAction fence) {
        this(runId, actor, deadline, clock, fence, result -> { });
    }

    public TaskExecutionContext(String runId, AccountAccess actor, long deadline,
                                LongSupplier clock, CheckedAction fence, Checkpoint checkpoint) {
        this.runId = Objects.requireNonNull(runId);
        this.actor = Objects.requireNonNull(actor);
        this.deadline = deadline;
        this.clock = Objects.requireNonNull(clock);
        this.fence = Objects.requireNonNull(fence);
        this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    public String runId() { return runId; }
    public AccountAccess actor() { return actor; }
    public long remainingMillis() { return Math.max(0, deadline - clock.getAsLong()); }
    public AdminAuditContext audit() {
        return new AdminAuditContext(actor.getUserId(), "", "/automation/" + runId, "ZrLog Assistant");
    }

    /** Providers call before side effects and between chunks of resumable work. */
    public void checkActive() throws Exception {
        if (remainingMillis() == 0 || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("automation.executionExpired");
        }
        fence.run();
    }

    /** Persist provider-owned progress before a side effect and after its result, without completing the task. */
    public void checkpoint(AutomationModels.Result progress) throws Exception {
        checkActive();
        checkpoint.save(Objects.requireNonNull(progress));
    }

    @FunctionalInterface
    public interface Checkpoint { void save(AutomationModels.Result progress) throws Exception; }

    @FunctionalInterface
    public interface CheckedAction { void run() throws Exception; }
}
