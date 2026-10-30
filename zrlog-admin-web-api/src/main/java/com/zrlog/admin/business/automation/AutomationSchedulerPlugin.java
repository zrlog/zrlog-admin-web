package com.zrlog.admin.business.automation;

import com.zrlog.plugin.IPlugin;
import com.zrlog.util.ThreadUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** Resident heartbeat only. Providers and Web API execution remain available without this timer. */
public final class AutomationSchedulerPlugin implements IPlugin {
    private static final Logger LOGGER = Logger.getLogger(AutomationSchedulerPlugin.class.getName());
    private final AutomationSettingsService settings;
    private final AutomationTickService ticks;
    private final BooleanSupplier faas;
    private ScheduledExecutorService executor;

    public AutomationSchedulerPlugin(AutomationSettingsService settings, AutomationTickService ticks, BooleanSupplier faas) {
        this.settings = settings;
        this.ticks = ticks;
        this.faas = faas;
    }

    @Override
    public boolean autoStart() { return !faas.getAsBoolean(); }

    @Override
    public synchronized boolean start() {
        if (executor != null || faas.getAsBoolean()) return true;
        executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = ThreadUtils.unstarted(runnable);
            thread.setName("assistant-automation-scheduler");
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                if (settings.internalEnabled()) ticks.tick("internal", null, 660000);
            } catch (Exception error) {
                LOGGER.warning("Assistant scheduler tick failed: " + error.getClass().getSimpleName());
            }
        }, 60, 60, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public synchronized boolean isStarted() { return executor != null; }

    @Override
    public synchronized boolean stop() {
        if (executor != null) { executor.shutdown(); executor = null; }
        return true;
    }
}
