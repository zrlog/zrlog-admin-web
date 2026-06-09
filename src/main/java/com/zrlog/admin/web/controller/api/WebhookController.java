package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
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
    public ApiStandardResponse<WebhookTokenResponse> token() {
        requirePost();
        WebhookTokenResponse response = webhookService.rotateToken();
        new AdminAuditService().record(request, AdminAuditAction.ROTATE_WEBHOOK_TOKEN);
        return new ApiStandardResponse<>(response);
    }

    @ResponseBody
    public ApiStandardResponse<WebhookConfigResponse> revokeToken() {
        requirePost();
        WebhookConfigResponse response = webhookService.revokeToken();
        new AdminAuditService().record(request, AdminAuditAction.REVOKE_WEBHOOK_TOKEN);
        return new ApiStandardResponse<>(response);
    }

    @ResponseBody
    public ApiStandardResponse<WebhookMessageNoticeCreateResponse> messageCenterNotice() {
        requirePost();
        if (!webhookService.verifyToken(readToken())) {
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
