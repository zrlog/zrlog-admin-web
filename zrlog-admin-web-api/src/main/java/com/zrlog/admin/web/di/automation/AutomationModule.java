package com.zrlog.admin.web.di.automation;

import com.zrlog.admin.business.automation.*;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Module
public final class AutomationModule {
    private AutomationModule() { }

    @Provides @Singleton
    static TaskRegistry registry(Supplier<List<ScheduledTaskProvider>> providers) { return new TaskRegistry(providers); }

    @Provides @Singleton
    static AutomationService service(AutomationStore store, TaskAuthority authority, TaskRegistry registry) {
        return new AutomationService(store, authority, registry, System::currentTimeMillis);
    }

    @Provides @Singleton
    static AutomationEngine engine(AutomationStore store, TaskAuthority authority, TaskRegistry registry) {
        return new AutomationEngine(store, authority, registry, System::currentTimeMillis);
    }

    @Provides @Singleton
    static AutomationSettingsService settings(AutomationStore store, TaskAuthority authority, BooleanSupplier faas) {
        return new AutomationSettingsService(store, authority, System::currentTimeMillis, faas);
    }

    @Provides @Singleton
    static AutomationTickService ticks(AutomationEngine engine, AutomationStore store, AutomationDefaults defaults) {
        return new AutomationTickService(engine, store, System::currentTimeMillis, defaults);
    }

    @Provides @Singleton
    static AutomationSchedulerPlugin plugin(AutomationSettingsService settings, AutomationTickService ticks, BooleanSupplier faas) {
        return new AutomationSchedulerPlugin(settings, ticks, faas);
    }
}
