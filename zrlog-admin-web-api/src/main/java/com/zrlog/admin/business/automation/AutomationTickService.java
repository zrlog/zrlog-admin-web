package com.zrlog.admin.business.automation;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Keeps last-trigger health separate from business runs; empty ticks do not fill execution history.
 */
public final class AutomationTickService {
    static final String RETENTION_KEY = "assistant.v2.retention";
    private final AutomationEngine engine;
    private final AutomationStore store;
    private final LongSupplier clock;
    private final AutomationDefaults defaults;
    private final Completion completion;

    public AutomationTickService(
            AutomationEngine engine, AutomationStore store, LongSupplier clock) {
        this(engine, store, clock, null);
    }

    public AutomationTickService(
            AutomationEngine engine,
            AutomationStore store,
            LongSupplier clock,
            AutomationDefaults defaults) {
        this(engine, store, clock, defaults, (budget, receipt) -> {});
    }

    public AutomationTickService(
            AutomationEngine engine,
            AutomationStore store,
            LongSupplier clock,
            AutomationDefaults defaults,
            Completion completion) {
        this.engine = engine;
        this.store = store;
        this.clock = clock;
        this.defaults = defaults;
        this.completion = completion;
    }

    public AutomationModels.TickResult tick(String source, String credentialId, long budgetMillis)
            throws SQLException {
        if (!List.of("internal", "external").contains(source)
                || budgetMillis < 1
                || budgetMillis > 660000)
            throw new IllegalArgumentException("automation.invalidTrigger");
        long deadline = Math.addExact(clock.getAsLong(), budgetMillis);
        String key = "assistant.v1.tick:" + source;
        var initial =
                store.readBatch(
                        List.of(
                                AutomationDefaults.KEY,
                                AutomationDefaults.PLUGIN_KEY,
                                key,
                                AutomaticUpgradeTaskProvider.KEY,
                                RETENTION_KEY));
        if (defaults != null) defaults.initialize(initial);
        Health health = new Health();
        health.id = UUID.randomUUID().toString();
        health.source = source;
        health.credentialId = credentialId;
        health.startedAt = clock.getAsLong();
        health.status = "running";
        AutomationStore.Snapshot<Health> before = initial.get(key, Health.class);
        var running = AutomationStore.Snapshot.capture(key, health, Health.class);
        boolean recorded = store.write(key, before, health);
        try {
            completion.run(
                    Math.max(0, deadline - clock.getAsLong()),
                    initial.get(
                            AutomaticUpgradeTaskProvider.KEY,
                            AutomaticUpgradeTaskProvider.Receipt.class));
            if (clock.getAsLong() < deadline) {
                retain(
                        initial.get(RETENTION_KEY, Retention.class),
                        initial.get(
                                AutomaticUpgradeTaskProvider.KEY,
                                AutomaticUpgradeTaskProvider.Receipt.class));
            }
            long remaining = deadline - clock.getAsLong();
            health.result =
                    remaining > 0
                            ? engine.tick(source, remaining)
                            : new AutomationModels.TickResult();
            if (remaining <= 0) health.result.budgetExhausted = true;
            health.status = "success";
            return health.result;
        } catch (SQLException | RuntimeException error) {
            health.status = "failed";
            throw error;
        } finally {
            health.finishedAt = clock.getAsLong();
            // Only replace the exact running record we successfully wrote; a newer tick wins.
            if (recorded) store.write(key, running, health);
        }
    }

    /** Once per day, with a durable claim shared by resident and FaaS triggers. */
    private void retain(
            AutomationStore.Snapshot<Retention> before,
            AutomationStore.Snapshot<AutomaticUpgradeTaskProvider.Receipt> receipt) {
        long now = clock.getAsLong();
        if (before != null && before.value.nextAt > now) return;
        Retention claimed = new Retention();
        claimed.nextAt = now + 60000;
        try {
            // A fresh store has no old runs. Establish its first maintenance date without a scan.
            if (before == null) claimed.nextAt = now + java.time.Duration.ofDays(1).toMillis();
            if (!store.write(RETENTION_KEY, before, claimed) || before == null) return;
            String pending =
                    receipt != null && !"confirmed".equals(receipt.value.state)
                            ? receipt.value.runId
                            : null;
            int removed = store.pruneRuns(null, now, pending);
            Retention next = new Retention();
            next.nextAt = now + (removed == 64 ? 60000 : java.time.Duration.ofDays(1).toMillis());
            store.write(
                    RETENTION_KEY,
                    AutomationStore.Snapshot.capture(RETENTION_KEY, claimed, Retention.class),
                    next);
        } catch (SQLException error) {
            java.util.logging.Logger.getLogger(AutomationTickService.class.getName())
                    .log(java.util.logging.Level.WARNING, "Assistant retention failed", error);
        }
    }

    public static final class Retention {
        public long nextAt;
    }

    public Health health(String source) throws SQLException {
        if (!List.of("internal", "external").contains(source))
            throw new IllegalArgumentException("automation.invalidTrigger");
        AutomationStore.Snapshot<Health> row =
                store.read("assistant.v1.tick:" + source, Health.class);
        return row == null ? null : row.value;
    }

    public static class Health {
        public String id;
        public String source;
        public String credentialId;
        public long startedAt;
        public Long finishedAt;
        public String status;
        public AutomationModels.TickResult result;
    }

    @FunctionalInterface
    public interface Completion {
        void run(
                long budgetMillis,
                AutomationStore.Snapshot<AutomaticUpgradeTaskProvider.Receipt> receipt)
                throws SQLException;
    }
}
