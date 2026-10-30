package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.automation.AutomationSettingsService;
import com.zrlog.admin.business.automation.AutomationTickService;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.common.rest.response.ApiStandardResponse;

/** Exact route with trigger-only authentication, before the browser session interceptor. */
public final class AutomationTickInterceptor implements HandleAbleInterceptor {
    public static final String PATH = com.zrlog.admin.business.AdminConstants.ADMIN_ASSISTANT_TASK_TRIGGER_API_URI_PATH;
    private final AutomationSettingsService settings;
    private final AutomationTickService ticks;

    public AutomationTickInterceptor(AutomationSettingsService settings, AutomationTickService ticks) {
        this.settings = settings;
        this.ticks = ticks;
    }

    @Override
    public boolean isHandleAble(HttpRequest request) { return PATH.equals(request.getUri()); }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        response.addHeader("Cache-Control", "no-store");
        if (request.getMethod() != HttpMethod.POST) {
            response.addHeader("Allow", "POST");
            response.renderCode(405);
            return false;
        }
        String tokenId;
        try { tokenId = settings.authenticate(request.getHeader("Authorization")); }
        catch (PermissionErrorException error) {
            response.addHeader("WWW-Authenticate", "Bearer");
            response.renderCode(401);
            return false;
        }
        response.renderJson(new ApiStandardResponse<>(ticks.tick("external", tokenId, settings.externalBudgetMillis())));
        return false;
    }
}
