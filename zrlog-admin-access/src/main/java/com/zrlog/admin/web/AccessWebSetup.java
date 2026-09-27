package com.zrlog.admin.web;

import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.OAuthAdminController;
import com.zrlog.admin.web.controller.api.WebhookController;
import com.zrlog.admin.web.interceptor.AdminCrossOriginInterceptor;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import java.util.List;

public class AccessWebSetup implements WebSetup {
    private final WebSetupContext context;
    public AccessWebSetup(WebSetupContext context) { this.context = context; }

    @Override public void setup() {
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
