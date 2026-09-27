package com.zrlog.admin.web.controller.ai;

import com.zrlog.admin.business.ai.service.AIConversationService;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.google.gson.Gson;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.service.AIChatService;
import com.zrlog.admin.business.ai.service.AIImageService;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.ai.service.AIPublishCheckService;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class AIArticleController extends BaseController {

    private final AIPublishCheckService publishingService = new AIPublishCheckService();

    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.POST)
    public void ai() throws IOException, InterruptedException, SQLException {
        String tool = request.getParaToStr("tool", "");
        AIStreamResponse streamResponse;
        if (StringUtils.isEmpty(tool) && StringUtils.isEmpty(request.getParaToStr("input", ""))) {
            java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
            if (body == null || body.remaining() > 256 * 1024) throw new ArgsException();
            streamResponse = new AIChatService().start(getRequestBodyWithNullCheck(
                    com.zrlog.admin.business.ai.model.AIChatModels.ChatRequest.class));
        } else {
            GenerateArticleFieldRequest articleContext = StringUtils.isNotEmpty(tool)
                    ? getRequestBodyWithNullCheck(GenerateArticleFieldRequest.class)
                    : null;
            if (Objects.equals(tool, "publishCheck")) {
                publishingService.fillPublishCheckContext(articleContext);
            }
            boolean includeArticleContext = !Objects.equals(request.getParaToStr("includeArticleContext", "true"), "false");
            streamResponse = new AIChatService().startStreamResponse(getParamWithEmptyCheck("input"),
                    aiContextId(), tool, articleContext, includeArticleContext);
        }
        AdminSseEmitter.setHeaders(response);
        response.addHeader("Cache-Control", "no-store, no-transform");
        if (streamResponse.getInputStream() == null) {
            String errorPayload = new Gson().toJson(AdminSsePayloads.error(1,
                    Objects.requireNonNullElse(streamResponse.getErrorBody(), "")));
            response.write(new ByteArrayInputStream(errorPayload.getBytes(StandardCharsets.UTF_8)),
                    streamResponse.getStatusCode());
            return;
        }
        response.write(streamResponse.getInputStream(), streamResponse.getStatusCode());
    }

    private long aiContextId() {
        long id = Long.parseLong(request.getParaToStr("id", "0"));
        return id == 0 ? -(long) AccountPermissionService.current().getUserId() : id;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.aiContext")
    public ApiStandardResponse<List<AIResponseEntry.AIContentEntry>> appendAiContext()
            throws SQLException {
        AddArticleAIContextRequest contextRequest = getRequestBodyWithNullCheck(AddArticleAIContextRequest.class);
        List<AIResponseEntry.AIContentEntry> messages = new AIConversationService().appendArticleContextMessage(
                aiContextId(), contextRequest);
        return new ApiStandardResponse<>(messages.stream()
                .filter(e -> !Objects.equals(e.getRole(), "system"))
                .collect(java.util.stream.Collectors.toList()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.aiMessage")
    public ApiStandardResponse<Boolean> updateAiMessage() throws SQLException {
        UpdateAIMessageRequest updateRequest = getRequestBodyWithNullCheck(UpdateAIMessageRequest.class);
        boolean updated = new AIConversationService().updateAIMessagePayload(aiContextId(),
                updateRequest.getMessageId(), updateRequest.getTool(), updateRequest.getPayload());
        return new ApiStandardResponse<>(updated);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.clearAiMessages")
    public ApiStandardResponse<Boolean> clearAiMessages() {
        boolean cleared = new AIConversationService().clearAIMessage(aiContextId());
        if (!cleared) throw new com.zrlog.admin.business.ai.exception.AIMessageSaveException();
        return new ApiStandardResponse<>(cleared);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.exportAiMessages")
    public ApiStandardResponse<ArticleAIMessageExportResponse> exportAiMessages() {
        return new ApiStandardResponse<>(
                new AIConversationService().exportAIMessage(aiContextId()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.applyCover")
    public ApiStandardResponse<UploadFileResponse> applyCover() throws SQLException {
        ApplyArticleCoverRequest coverRequest = getRequestBodyWithNullCheck(ApplyArticleCoverRequest.class);
        UploadFileResponse uploadFileResponse = new AIImageService().applyArticleCover(coverRequest, getRequest(),
                aiContextId());
        return new ApiStandardResponse<>(uploadFileResponse);
    }

}
