package com.zrlog.admin.web.controller.ai;

import com.google.gson.Gson;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.ai.service.AIChatService;
import com.zrlog.admin.business.ai.service.AIConversationService;
import com.zrlog.admin.business.ai.service.AIFileService;
import com.zrlog.admin.business.ai.service.AIImageService;
import com.zrlog.admin.business.ai.service.AIPublishCheckService;
import com.zrlog.admin.business.ai.service.AIReferenceService;
import com.zrlog.admin.business.knowledge.KnowledgeModels;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class AIArticleController extends BaseController {
    private final AIConversationService conversations;

    private final AIFileService aIFileService;
    private final AIImageService aIImageService;

    private final AccountPermissionService permissionService;

    private final UploadService uploadService;
    private final AIChatService chat;
    private final AIReferenceService references;

    @Inject
    public AIArticleController(
            UploadService uploadService,
            AIChatService chat,
            AIReferenceService references,
            AccountPermissionService permissionService,
            AIFileService aIFileService,
            AIPublishCheckService publishingService,
            AIImageService aIImageService,
            AIConversationService conversations) {
        this.conversations = conversations;

        this.aIFileService = aIFileService;
        this.publishingService = publishingService;
        this.aIImageService = aIImageService;

        this.permissionService = permissionService;

        this.references = references;
        this.chat = chat;
        this.uploadService = uploadService;
    }

    private final AIPublishCheckService publishingService;

    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.POST)
    public void ai() throws IOException, InterruptedException, SQLException {
        String tool = request.getParaToStr("tool", "");
        AIStreamResponse streamResponse;
        if (StringUtils.isEmpty(tool) && StringUtils.isEmpty(request.getParaToStr("input", ""))) {
            java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
            if (body == null || body.remaining() > AIFileService.MAX_REQUEST_BYTES)
                throw new ArgsException();
            streamResponse =
                    chat.start(getRequestBodyWithNullCheck(AIChatModels.ChatRequest.class));
        } else {
            GenerateArticleFieldRequest articleContext = StringUtils.isNotEmpty(tool)
                    ? getRequestBodyWithNullCheck(GenerateArticleFieldRequest.class)
                    : null;
            if (Objects.equals(tool, "publishCheck")) {
                publishingService.fillPublishCheckContext(articleContext);
            }
            streamResponse = chat.startStreamResponse(getParamWithEmptyCheck("input"),
                    aiContextId(), tool, articleContext);
        }
        writeStream(streamResponse);
    }

    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.POST)
    public void approveAiOperation() throws IOException, SQLException {
        java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
        if (body == null || body.remaining() > 4096) throw new ArgsException();
        writeStream(chat.resume(getRequestBodyWithNullCheck(AIChatModels.ApprovalRequest.class)));
    }

    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.POST)
    public void submitAiInput() throws IOException, SQLException {
        java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
        if (body == null || body.remaining() > 64 * 1024) throw new ArgsException();
        writeStream(chat.resumeInput(getRequestBodyWithNullCheck(AIChatModels.InputRequest.class)));
    }

    @ResponseBody
    @RequiresAction(
            value = AccountAction.ARTICLE_ASSIST,
            articleQuery = true,
            descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.GET)
    public ApiStandardResponse<AIChatModels.RunView> aiRun() throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        return new ApiStandardResponse<>(chat.getRun(Long.parseLong(request.getParaToStr("id", "0"))));
    }

    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.GET)
    public void aiFile() throws SQLException, IOException {
        response.addHeader("Cache-Control", "private, no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        var file = chat.imageAttachment(Long.parseLong(request.getParaToStr("id", "0")),
                getParamWithEmptyCheck("fileId"));
        byte[] bytes = this.aIFileService.read(file);
        response.setContentType(file.mimeType);
        response.write(new ByteArrayInputStream(bytes));
    }

    private void writeStream(AIStreamResponse streamResponse) throws IOException {
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
        return id == 0 ? -(long) permissionService.current().getUserId() : id;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "article.assist")
    @RequestMethod(method = HttpMethod.GET)
    public ApiStandardResponse<KnowledgeModels.SearchResult> aiReferences() throws SQLException {
        response.addHeader("Cache-Control", "private, no-store");
        return new ApiStandardResponse<>(references.search(request.getParaToStr("query", "")));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.aiMessage")
    public ApiStandardResponse<Boolean> updateAiMessage() throws SQLException {
        UpdateAIMessageRequest updateRequest = getRequestBodyWithNullCheck(UpdateAIMessageRequest.class);
        boolean updated =
                this.conversations.updateAIMessagePayload(
                        aiContextId(),
                        updateRequest.getMessageId(),
                        updateRequest.getTool(),
                        updateRequest.getPayload());
        return new ApiStandardResponse<>(updated);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.clearAiMessages")
    public ApiStandardResponse<Boolean> clearAiMessages() {
        boolean cleared = this.conversations.clearAIMessage(aiContextId());
        if (!cleared) throw new AIMessageSaveException();
        return new ApiStandardResponse<>(cleared);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.exportAiMessages")
    public ApiStandardResponse<ArticleAIMessageExportResponse> exportAiMessages() {
        return new ApiStandardResponse<>(this.conversations.exportAIMessage(aiContextId()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.applyCover")
    public ApiStandardResponse<UploadFileResponse> applyCover() throws SQLException {
        ApplyArticleCoverRequest coverRequest = getRequestBodyWithNullCheck(ApplyArticleCoverRequest.class);
        UploadFileResponse uploadFileResponse =
                this.aIImageService.applyArticleCover(
                        coverRequest, getRequest(), aiContextId(), uploadService);
        return new ApiStandardResponse<>(uploadFileResponse);
    }

}
