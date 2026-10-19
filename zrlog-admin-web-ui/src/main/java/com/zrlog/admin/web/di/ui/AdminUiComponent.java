package com.zrlog.admin.web.di.ui;

import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.controller.api.AdminStaticSiteController;
import com.zrlog.admin.web.controller.page.*;
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.TokenService;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Component(
        modules = {
            DataBindingsModule.class,
            ServiceBindingsModule.class,
            AccountBindingsModule.class,
            AdminUiModule.class
        })
public interface AdminUiComponent {
    AdminPageService pages();

    AdminPageController pageController();

    AdminTemplatePageController templates();

    AdminManifestController manifest();

    AdminStaticSiteController staticSites();

    AdminPluginInterceptor pluginInterceptor();

    @Component.Factory
    interface Factory {
        AdminUiComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance AdminComponent admin,
                @BindsInstance TokenService tokens,
                @BindsInstance Supplier<PluginCorePlugin> plugins,
                @BindsInstance Supplier<AdminStaticResourcePlugin> staticSites,
                @BindsInstance Supplier<AdminStaticSiteSsePublisher> publisher,
                @BindsInstance Supplier<String> buildId,
                @BindsInstance @Named("installed") BooleanSupplier installed);
    }
}
