package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.annotation.RequestMethod;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.WebhookConfigRequest;
import com.zrlog.admin.business.rest.request.WebhookMessageNoticeRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.util.I18nUtil;

public class WebhookController extends BaseController {

    private final WebhookService webhookService = new WebhookService();

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "webhook.configure")
    public StandardResponse config() {
        if (request.getMethod() == HttpMethod.POST) {
            WebhookConfigRequest configRequest = getRequestBodyWithNullCheck(WebhookConfigRequest.class);
            WebhookConfigResponse response = webhookService.updateConfig(configRequest);
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_WEBHOOK_CONFIG);
            return new ApiStandardResponse<>(
                    response,
                    I18nUtil.getAdminBackendStringFromRes("admin.common.update.success")
            );
        }
        return new AdminPageDataResponse<>(webhookService.getConfigResponse(), "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "webhook.rotateToken")
    public ApiStandardResponse<WebhookTokenResponse> token() {
        requirePost();
        WebhookTokenResponse response = webhookService.rotateToken();
        new AdminAuditService().record(request, AdminAuditAction.ROTATE_WEBHOOK_TOKEN);
        return new ApiStandardResponse<>(response);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "webhook.revokeToken")
    public ApiStandardResponse<WebhookConfigResponse> revokeToken() {
        requirePost();
        WebhookConfigResponse response = webhookService.revokeToken();
        new AdminAuditService().record(request, AdminAuditAction.REVOKE_WEBHOOK_TOKEN);
        return new ApiStandardResponse<>(response);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.NOTIFICATION_CREATE, descriptionKey = "webhook.createNotice")
    public ApiStandardResponse<WebhookMessageNoticeCreateResponse> messageCenterNotice() {
        requirePost();
        boolean personal = com.zrlog.admin.business.security.DelegatedAccess.active();
        if (personal) com.zrlog.admin.business.service.AccountPermissionService.require(AccountAction.NOTIFICATION_CREATE);
        if (!webhookService.getConfigResponse().getEnabled() || (!personal && !webhookService.verifyToken(readToken()))) {
            throw new PermissionErrorException();
        }
        WebhookMessageNoticeRequest noticeRequest = getRequestBodyWithNullCheck(WebhookMessageNoticeRequest.class);
        return new ApiStandardResponse<>(webhookService.createMessageCenterNotice(noticeRequest));
    }

    private String readToken() {
        String token = request.getHeader(WebhookService.TOKEN_HEADER);
        if (StringUtils.isNotEmpty(token)) {
            return token.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotEmpty(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return request.getParaToStr("token", "");
    }

    private void requirePost() {
        if (request.getMethod() != HttpMethod.POST) {
            throw new ArgsException("method");
        }
    }
}
