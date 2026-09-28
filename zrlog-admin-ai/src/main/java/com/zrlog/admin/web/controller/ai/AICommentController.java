package com.zrlog.admin.web.controller.ai;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.service.AICommentService;
import com.zrlog.admin.business.rest.request.AnalyzeCommentRequest;
import com.zrlog.admin.business.rest.response.AnalyzeCommentResponse;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.interceptor.PluginTokenValidator;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.io.IOException;

public class AICommentController extends BaseController {
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequestLock(onlyOnPostMethod = true)
    public ApiStandardResponse<AnalyzeCommentResponse> analyze() throws IOException, InterruptedException {
        PluginTokenValidator.validate(request);
        return new ApiStandardResponse<>(new AICommentService().analyze(getRequestBodyWithNullCheck(AnalyzeCommentRequest.class)));
    }
}
