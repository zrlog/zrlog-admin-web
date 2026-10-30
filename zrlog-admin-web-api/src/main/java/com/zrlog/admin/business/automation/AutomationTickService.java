package com.zrlog.admin.business.automation;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Keeps last-trigger health separate from business runs; empty ticks do not fill execution history. */
public final class AutomationTickService {
    private final AutomationEngine engine;
    private final AutomationStore store;
    private final LongSupplier clock;
    private final AutomationDefaults defaults;
    private final Completion completion;

    public AutomationTickService(AutomationEngine engine, AutomationStore store, LongSupplier clock) {
        this(engine, store, clock, null);
    }

    public AutomationTickService(AutomationEngine engine, AutomationStore store, LongSupplier clock, AutomationDefaults defaults) {
        this(engine, store, clock, defaults, budget -> { });
    }

    public AutomationTickService(AutomationEngine engine, AutomationStore store, LongSupplier clock,
                                 AutomationDefaults defaults, Completion completion) {
        this.engine = engine; this.store = store; this.clock = clock; this.defaults = defaults;
        this.completion = completion;
    }

    public AutomationModels.TickResult tick(String source, String credentialId, long budgetMillis) throws SQLException {
        if (!List.of("internal", "external").contains(source) || budgetMillis < 1 || budgetMillis > 660000)
            throw new IllegalArgumentException("automation.invalidTrigger");
        long deadline = Math.addExact(clock.getAsLong(), budgetMillis);
        if (defaults != null) { defaults.initialize(); defaults.initializePluginTrigger(); }
        String key = "assistant.v1.tick:" + source;
        Health health = new Health();
        health.id = UUID.randomUUID().toString();
        health.source = source;
        health.credentialId = credentialId;
        health.startedAt = clock.getAsLong();
        health.status = "running";
        AutomationStore.Snapshot<Health> before = store.read(key, Health.class);
        store.write(key, before, health);
        try {
            completion.run(Math.max(0, deadline - clock.getAsLong()));
            long remaining = deadline - clock.getAsLong();
            health.result = remaining > 0 ? engine.tick(source, remaining) : new AutomationModels.TickResult();
            if (remaining <= 0) health.result.budgetExhausted = true;
            health.status = "success";
            return health.result;
        } catch (SQLException | RuntimeException error) {
            health.status = "failed";
            throw error;
        } finally {
            health.finishedAt = clock.getAsLong();
            AutomationStore.Snapshot<Health> current = store.read(key, Health.class);
            if (current != null && health.id.equals(current.value.id)) store.write(key, current, health);
        }
    }

    public Health health(String source) throws SQLException {
        if (!List.of("internal", "external").contains(source)) throw new IllegalArgumentException("automation.invalidTrigger");
        AutomationStore.Snapshot<Health> row = store.read("assistant.v1.tick:" + source, Health.class);
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
    public interface Completion { void run(long budgetMillis) throws SQLException; }
}
