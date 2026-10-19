package com.zrlog.admin.web.di;

import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.SystemInfoUtils;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.blog.web.plugin.TemplateDownloadPlugin;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;
import com.zrlog.updater.spi.DockerControl;

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
            AdminModule.class
        })
public interface AdminComponent {
    AdminDevController adminDevController();

    AdminProxyController adminProxyController();

    AdminPublicController adminPublicController();

    AdminSystemController adminSystemController();

    TemplateController templateController();

    MessageCenterController messageCenterController();

    UpgradeController upgradeController();

    PluginUploadController pluginUploadController();

    AdminController adminController();

    WebSiteController webSiteController();

    AdminDashboardService dashboard();

    AdminStatisticsService statistics();

    FeatureLabService features();

    AdminResourceService resources();

    TemplateService templates();

    WebSiteSettingsService settings();

    UpgradeSettingsService upgradeSettings();

    MessageCenterService messages();

    @Component.Factory
    interface Factory {
        AdminComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance Supplier<ContentComponent> content,
                @BindsInstance TokenService tokens,
                @BindsInstance Supplier<PluginCorePlugin> plugins,
                @BindsInstance Supplier<TemplateDownloadPlugin> templates,
                @BindsInstance Supplier<UpdateVersionInfoPlugin> updates,
                @BindsInstance Supplier<DockerControl> docker,
                @BindsInstance SystemInfoUtils systemInfo,
                @BindsInstance BooleanSupplier staticHtml,
                @BindsInstance @Named("accessAvailable") BooleanSupplier accessAvailable,
                @BindsInstance Supplier<AdminResourceInfoResponse.Capabilities> capabilities,
                @BindsInstance Supplier<PublicWebSiteInfo> site);
    }
}
