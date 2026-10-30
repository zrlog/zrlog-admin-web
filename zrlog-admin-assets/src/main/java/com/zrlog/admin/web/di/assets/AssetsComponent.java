package com.zrlog.admin.web.di.assets;

import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.ArticleResourceService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.web.controller.api.FileManagerController;
import com.zrlog.admin.web.controller.api.UploadController;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.AdminTemporaryResourceInterceptor;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.TokenService;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;
import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Singleton;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Component(
        modules = {
                DataBindingsModule.class,
                ServiceBindingsModule.class,
                AccountBindingsModule.class
        })
public interface AssetsComponent {
    UploadService uploads();

    com.zrlog.admin.business.service.TemporaryCleanupTaskProvider cleanupTasks();

    AccountPermissionService permissions();

    AdminRequestIdentity requestIdentity();

    UploadController uploadController();

    FileManagerController fileManagerController();

    AdminTemporaryResourceInterceptor temporaryResources();

    @Component.Factory
    interface Factory {
        AssetsComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance Supplier<ArticleResourceService> articleResources,
                @BindsInstance Supplier<PluginCorePlugin> plugins,
                @BindsInstance Supplier<TokenService> tokens,
                @BindsInstance BooleanSupplier staticHtml,
                @BindsInstance String contextPath);
    }
}
