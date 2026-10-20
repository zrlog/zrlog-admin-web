package com.zrlog.admin.web.di.access;

import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.web.controller.api.OAuthAdminController;
import com.zrlog.admin.web.controller.api.WebhookController;
import com.zrlog.admin.web.di.account.AccountBindingsModule;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.AdminActionDispatcher;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.interceptor.McpInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
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
                AccessModule.class
        })
public interface AccessComponent {
    OAuthService oauth();

    OAuthAdminController oauthController();

    WebhookController webhooks();

    OAuthInterceptor oauthInterceptor();

    BearerTokenInterceptor bearerTokens();

    McpInterceptor mcp();

    @Component.Factory
    interface Factory {
        AccessComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance AccountComponent account,
                @BindsInstance BooleanSupplier mcpEnabled,
                @BindsInstance Supplier<AdminActionDispatcher> dispatcher,
                @BindsInstance Supplier<ContentToolFactory> tools,
                @BindsInstance @Named("siteTitle") Supplier<String> title);
    }
}
