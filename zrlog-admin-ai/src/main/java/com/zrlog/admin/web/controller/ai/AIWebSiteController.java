package com.zrlog.admin.web.controller.ai;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.service.AIConfigService;
import com.zrlog.admin.business.ai.service.AIToolService;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.OptimizeAiPromptRequest;
import com.zrlog.admin.business.rest.request.OptimizeWebsiteDescriptionRequest;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.OptimizeAiPromptResponse;
import com.zrlog.admin.business.rest.response.OptimizeWebsiteDescriptionResponse;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.sql.SQLException;

public class AIWebSiteController extends BaseController {

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.optimizeDescription")
    public ApiStandardResponse<OptimizeWebsiteDescriptionResponse> optimizeDescription()
            throws IOException, InterruptedException {
        OptimizeWebsiteDescriptionRequest body = getRequestBodyWithNullCheck(OptimizeWebsiteDescriptionRequest.class);
        return new ApiStandardResponse<>(new AIToolService().optimizeWebsiteDescription(body));
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.optimizePrompt")
    public ApiStandardResponse<OptimizeAiPromptResponse> optimizeAiPrompt()
            throws IOException, InterruptedException {
        OptimizeAiPromptRequest body = getRequestBodyWithNullCheck(OptimizeAiPromptRequest.class);
        return new ApiStandardResponse<>(new AIToolService().optimizeAiPrompt(body));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.ai")
    public AdminPageDataResponse<AIWebSiteInfoResponse> ai() throws SQLException {
        if (isPost()) {
            new AIConfigService().updateAi(getRequestBodyWithNullCheck(AIWebSiteInfo.class), request);
        }
        return page(new AIConfigService().aiResponse());
    }

    private boolean isPost() {
        return request.getMethod() == HttpMethod.POST;
    }

    private <T> AdminPageDataResponse<T> page(T data) {
        String message = isPost() ? I18nUtil.getAdminBackendStringFromRes("admin.common.update.success") : "";
        return new AdminPageDataResponse<>(data, message, request.getUri());
    }
}
