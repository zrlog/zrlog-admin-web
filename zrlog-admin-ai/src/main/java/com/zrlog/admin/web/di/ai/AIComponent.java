package com.zrlog.admin.web.di.ai;

import com.zrlog.admin.business.ai.service.*;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.PluginTokenValidator;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Singleton;

@Singleton
@Component(
        modules = {
            DataBindingsModule.class,
            ServiceBindingsModule.class,
            AccountBindingsModule.class
        })
public interface AIComponent {
    AIConversationService conversations();

    AIPublishCheckService publishing();

    AIReferenceService references();

    AIRequestComponent.Factory requests();

    @Component.Factory
    interface Factory {
        AIComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance ContentToolFactory tools,
                @BindsInstance UploadService uploads,
                @BindsInstance WebSiteSettingsService settings,
                @BindsInstance PluginTokenValidator pluginTokens);
    }
}
