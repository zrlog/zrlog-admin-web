package com.zrlog.admin.web.di.automation;

import com.zrlog.admin.business.automation.*;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;
import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Component(modules = {DataBindingsModule.class, AccountBindingsModule.class, AutomationModule.class,
        com.zrlog.business.di.ServiceBindingsModule.class})
public interface AutomationComponent {
    com.zrlog.admin.web.controller.api.AutomationController controller();
    AutomationService tasks();
    AutomationEngine engine();
    AutomationSettingsService settings();
    AutomationTickService ticks();
    AutomationSchedulerPlugin plugin();
    AutomationStore store();
    AutomationDefaults defaults();
    TaskRegistry registry();
    AutomationProposalService proposals();

    @Component.Factory
    interface Factory {
        AutomationComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance AccountComponent account,
                @BindsInstance com.zrlog.business.di.ServiceComponent services,
                @BindsInstance Supplier<List<ScheduledTaskProvider>> providers,
                @BindsInstance BooleanSupplier faas,
                @BindsInstance Supplier<AutomationAssistant> assistant);
    }
}
