package com.zrlog.admin.web;

import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.web.controller.api.OAuthAdminController;
import com.zrlog.admin.web.controller.api.WebhookController;
import com.zrlog.admin.web.di.access.AccessComponent;
import com.zrlog.admin.web.di.access.DaggerAccessComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.AdminActionDispatcher;
import com.zrlog.admin.web.interceptor.AdminCrossOriginInterceptor;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.data.di.DataComponent;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.List;

public class AccessWebSetup implements WebSetup {
    private final WebSetupContext context;

    public AccessWebSetup(WebSetupContext context) {
        this.context = context;
    }

    @Override
    public void setup() {
        context.getZrLogConfig()
                .getServerConfig()
                .getBindings()
                .singleton(
                        AccessComponent.class,
                        r ->
                                DaggerAccessComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                () ->
                                                        context.getZrLogConfig()
                                                                .getWebSetup(
                                                                        McpWebSetup.class)
                                                                != null,
                                                () -> r.get(AdminActionDispatcher.class),
                                                () -> r.get(ContentToolFactory.class),
                                                () -> {
                                                    var provider =
                                                            context.getZrLogConfig()
                                                                    .getWebSetup(
                                                                            WebSiteInfoProvider
                                                                                    .class);
                                                    var site =
                                                            provider == null
                                                                    ? null
                                                                    : provider
                                                                    .getPublicWebSiteInfo();
                                                    return site == null ? "" : site.getTitle();
                                                }))
                .singleton(OAuthService.class, r -> r.get(AccessComponent.class).oauth())
                .controller(
                        WebhookController.class,
                        (r, request, response) -> r.get(AccessComponent.class).webhooks())
                .interceptor(
                        OAuthInterceptor.class,
                        r -> r.get(AccessComponent.class).oauthInterceptor())
                .interceptor(
                        BearerTokenInterceptor.class,
                        r -> r.get(AccessComponent.class).bearerTokens())
                .controller(
                        OAuthAdminController.class,
                        (r, request, response) -> r.get(AccessComponent.class).oauthController());
        List<Class<? extends Interceptor>> interceptors = context.getZrLogConfig().getServerConfig().getInterceptors();
        interceptors.add(0, OAuthInterceptor.class);
        interceptors.add(interceptors.indexOf(AdminCrossOriginInterceptor.class) + 1, BearerTokenInterceptor.class);
        Router router = context.getZrLogConfig().getServerConfig().getRouter();
        router.addMapper("/api/admin/oauth", OAuthAdminController.class);
        router.addMapper("/api/admin/oauth/authorize", OAuthAdminController.class, "consent");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/webhook", WebhookController.class, "config");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook", WebhookController.class, "config");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook/token", WebhookController.class, "token");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook/token/revoke", WebhookController.class, "revokeToken");
        router.addMapper("/api/webhook/message-center/notice", WebhookController.class, "messageCenterNotice");
    }
}
