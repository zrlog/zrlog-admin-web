package com.zrlog.admin.web.controller.api;

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
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.service.ArticlePublishingService.PublishCheckTask;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AdminArticleController extends BaseController {

    private static final long PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);

    private final AdminArticleService articleService = new AdminArticleService();
    private final ArticlePublishingService publishingService = new ArticlePublishingService();

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_DELETE, articleIds = true, descriptionKey = "article.delete")
    @RequestMethod(method = HttpMethod.POST)
    public DeleteResponse delete() throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        String idStr = getParamWithEmptyCheck("id");
        if (StringUtils.isEmpty(idStr)) {
            throw new ArgsException("id");
        }
        return publishingService.deleteArticles(idStr, request);
    }

    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ARTICLE_CREATE, conditional = AccountAction.ARTICLE_PUBLISH, descriptionKey = "article.create")
    public void create() throws SQLException, IOException {
        CreateArticleRequest body = getRequestBodyWithNullCheck(CreateArticleRequest.class);
        publishingService.prepareRequest(body);
        AdminTokenVO adminToken = AdminTokenThreadLocal.getUser();
        if (publishingService.shouldUseTransparentPublishStream(body)) {
            writeTransparentPublishStream(body, () -> publishingService.create(adminToken, body, request));
            return;
        }
        CreateOrUpdateArticleResponse create = publishingService.create(adminToken, body, request);
        renderArticleSaveResponse(body, create);
    }

    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ARTICLE_UPDATE, conditional = AccountAction.ARTICLE_PUBLISH, descriptionKey = "article.update")
    public void update() throws SQLException, IOException {
        UpdateArticleRequest body = getRequestBodyWithNullCheck(UpdateArticleRequest.class);
        publishingService.prepareRequest(body);
        AdminTokenVO adminToken = AdminTokenThreadLocal.getUser();
        if (publishingService.shouldUseTransparentPublishStream(body)) {
            writeTransparentPublishStream(body, () -> publishingService.update(adminToken, body, request));
            return;
        }
        CreateOrUpdateArticleResponse update = publishingService.update(adminToken, body, request);
        renderArticleSaveResponse(body, update);
    }

    private void renderArticleSaveResponse(CreateArticleRequest body, CreateOrUpdateArticleResponse saveResponse)
            throws SQLException, IOException {
        boolean transparentPublish = publishingService.isTransparentPublish(body, saveResponse);
        AdminPageDataResponse<ArticleGlobalResponse> detail =
                publishingService.articleResponse(saveResponse, !transparentPublish, request);
        if (!transparentPublish) {
            response.renderJson(detail);
            return;
        }
        writeTransparentPublishStream(detail, body);
    }

    private void writeTransparentPublishStream(CreateArticleRequest body, ArticleSaveTask saveTask) throws IOException {
        AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef = new AtomicReference<>();
        writeTransparentPublishStream(body, detailRef, emitter -> {
            CreateOrUpdateArticleResponse saveResponse = saveTask.save();
            AdminPageDataResponse<ArticleGlobalResponse> detail =
                    publishingService.articleResponse(saveResponse, false, request);
            detailRef.set(detail);
            emitter.send("publish-start", AdminSsePayloads.message(detail.getMessage()));
            emitter.send("article", detail);
            return detail;
        });
    }

    private void writeTransparentPublishStream(AdminPageDataResponse<ArticleGlobalResponse> detail,
                                               CreateArticleRequest body)
            throws IOException {
        AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef = new AtomicReference<>(detail);
        writeTransparentPublishStream(body, detailRef, emitter -> {
            emitter.send("publish-start", AdminSsePayloads.message(detail.getMessage()));
            emitter.send("article", detail);
            return detail;
        });
    }

    private void writeTransparentPublishStream(CreateArticleRequest body,
                                               AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef,
                                               PublishStartWriter publishStartWriter)
            throws IOException {
        List<StaticSiteType> siteTypes = List.of(StaticSiteType.BLOG);
        AtomicReference<PublishCheckTask> publishCheckTaskRef = new AtomicReference<>();
        AtomicBoolean publishCheckSent = new AtomicBoolean(false);
        AdminStaticSiteSsePublisher.write(
                response,
                "transparent-publish",
                "publish-error",
                "static-error",
                siteTypes,
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = publishStartWriter.write(emitter);
                    PublishCheckTask publishCheckTask = publishingService.startPublishCheck(detail, body);
                    if (publishCheckTask != null) {
                        publishCheckTaskRef.set(publishCheckTask);
                        emitter.send("publish-check-start", AdminSsePayloads.tool("publishCheck"));
                    }
                },
                () -> publishingService.updateBlogCacheWithStaticSyncNotice(request),
                emitter -> sendPublishCheckIfReady(publishCheckTaskRef.get(), publishCheckSent, emitter, false),
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = detailRef.get();
                    sendPublishCheckIfReady(publishCheckTaskRef.get(), publishCheckSent, emitter, true);
                    emitter.send("publish-complete", AdminSsePayloads.message(detail.getMessage()));
                }
        );
    }

    @FunctionalInterface
    private interface ArticleSaveTask {

        CreateOrUpdateArticleResponse save() throws Exception;
    }

    @FunctionalInterface
    private interface PublishStartWriter {

        AdminPageDataResponse<ArticleGlobalResponse> write(AdminSseEmitter emitter) throws Exception;
    }

    private void sendPublishCheckIfReady(PublishCheckTask publishCheckTask,
                                         AtomicBoolean publishCheckSent, AdminSseEmitter emitter, boolean wait)
            throws Exception {
        sendPublishCheckIfReady(publishCheckTask, publishCheckSent, emitter, wait,
                PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS);
    }

    private void sendPublishCheckIfReady(PublishCheckTask publishCheckTask,
                                         AtomicBoolean publishCheckSent, AdminSseEmitter emitter, boolean wait,
                                         long timeoutMillis) throws Exception {
        if (publishCheckTask == null || (!wait && !publishCheckTask.getFuture().isDone())) {
            return;
        }
        if (!publishCheckSent.compareAndSet(false, true)) {
            return;
        }
        try {
            PublishCheckResponse result = wait
                    ? publishCheckTask.getFuture().get(timeoutMillis, TimeUnit.MILLISECONDS)
                    : publishCheckTask.getFuture().join();
            emitter.send("publish-check-complete", result);
        } catch (CompletionException | ExecutionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = StringUtils.isNotEmpty(cause.getMessage())
                    ? cause.getMessage()
                    : I18nUtil.getAdminBackendStringFromRes("admin.article.publishCheck.error.failed");
            if (publishCheckTask.getArticleId() != null) {
                publishingService.recordPublishCheckError(
                        publishCheckTask.getArticleId(), publishCheckTask.getArticleTitle(), message);
            }
            emitter.send("publish-check-error", AdminSsePayloads.message(message));
        } catch (CancellationException e) {
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.cancelled");
        } catch (TimeoutException e) {
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.interrupted");
        }
    }

    private void sendCancelledPublishCheck(PublishCheckTask publishCheckTask, AdminSseEmitter emitter,
                                           String messageKey) throws IOException {
        PublishCheckResponse committedResponse = publishCheckTask.getPersistenceGuard().cancelOrGetCommitted();
        if (committedResponse != null) {
            emitter.send("publish-check-complete", committedResponse);
            return;
        }
        publishCheckTask.getFuture().cancel(true);
        String message = I18nUtil.getAdminBackendStringFromRes(messageKey);
        if (publishCheckTask.getArticleId() != null) {
            publishingService.recordPublishCheckError(
                    publishCheckTask.getArticleId(), publishCheckTask.getArticleTitle(), message);
        }
        emitter.send("publish-check-error", AdminSsePayloads.message(message));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_READ, descriptionKey = "article.list")
    public AdminPageDataResponse<ArticlePageData> index()
            throws SQLException, ExecutionException, InterruptedException {
        String key = request.getParaToStr("key", "");
        String types = request.getParaToStr("types", "");
        String status = request.getParaToStr("status", "");
        int pageSize = articleService.resolveAdminPageSize(request.getParaToInt("size", -1));
        ArticlePageData pageData = articleService.adminPage(ControllerUtil.toPageRequest(this, pageSize), key, types,
                status, request);
        return new AdminPageDataResponse<>(pageData, "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, descriptionKey = "article.editor")
    public AdminPageDataResponse<ArticleGlobalResponse> articleEdit() throws SQLException {
        String id = request.getParaToStr("id", "");
        return articleService.loadDetailById(id, request);
    }

    /**
     * 仅保留，便于测试
     *
     * @return
     * @throws SQLException
     */
    @ResponseBody
    @Deprecated
    @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, descriptionKey = "article.detail")
    public AdminPageDataResponse<LoadEditArticleResponse> detail() throws SQLException {
        return new AdminPageDataResponse<>(articleService.loadDetail(getParamWithEmptyCheck("id"), request));
    }

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
        List<AIResponseEntry.AIContentEntry> messages = new WebSiteService().appendArticleContextMessage(
                aiContextId(), contextRequest);
        return new ApiStandardResponse<>(messages.stream()
                .filter(e -> !Objects.equals(e.getRole(), "system"))
                .collect(java.util.stream.Collectors.toList()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.aiMessage")
    public ApiStandardResponse<Boolean> updateAiMessage() throws SQLException {
        UpdateAIMessageRequest updateRequest = getRequestBodyWithNullCheck(UpdateAIMessageRequest.class);
        boolean updated = new WebSiteService().updateAIMessagePayload(aiContextId(),
                updateRequest.getMessageId(), updateRequest.getTool(), updateRequest.getPayload());
        return new ApiStandardResponse<>(updated);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.clearAiMessages")
    public ApiStandardResponse<Boolean> clearAiMessages() {
        boolean cleared = new WebSiteService().clearAIMessage(aiContextId());
        if (!cleared) throw new com.zrlog.admin.business.ai.exception.AIMessageSaveException();
        return new ApiStandardResponse<>(cleared);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, articleQuery = true, descriptionKey = "article.exportAiMessages")
    public ApiStandardResponse<ArticleAIMessageExportResponse> exportAiMessages() {
        return new ApiStandardResponse<>(
                new WebSiteService().exportAIMessage(aiContextId()));
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
