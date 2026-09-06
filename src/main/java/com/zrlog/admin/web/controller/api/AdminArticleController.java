package com.zrlog.admin.web.controller.api;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.service.AIChatService;
import com.zrlog.admin.business.ai.service.AIImageService;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.model.WebSite;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminArticleController extends BaseController {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminArticleController.class);
    private static final MarkdownJsRenderer MARKDOWN_RENDERER = new MarkdownJsRenderer();
    private static final long PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);

    private final AdminArticleService articleService = new AdminArticleService();

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    public DeleteResponse delete() throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        String idStr = getParamWithEmptyCheck("id");
        if (StringUtils.isEmpty(idStr)) {
            throw new ArgsException("id");
        }
        String[] ids = idStr.split(",");
        boolean deleted = Arrays.stream(ids).allMatch(id -> {
            try {
                return articleService.delete(Long.valueOf(id));
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        });
        new AdminAuditService().record(request, AdminAuditAction.DELETE_ARTICLE, idStr);
        return new DeleteResponse(deleted);
    }

    private String getResponseMsg(CreateOrUpdateArticleResponse response) {
        return I18nUtil.getAdminBackendStringFromRes(Objects.equals(response.getRubbish(), true)
                || Objects.equals(response.getPrivacy(), true) ? "admin.article.save.success" : "admin.article.release.success");
    }

    private AdminPageDataResponse<ArticleGlobalResponse> toResponseByArticle(
            CreateOrUpdateArticleResponse createOrUpdateArticleResponse, boolean refreshCache) throws SQLException {
        AdminPageDataResponse<ArticleGlobalResponse> detail = articleService
                .loadDetailById(createOrUpdateArticleResponse.getLogId() + "", request);
        if (refreshCache && createOrUpdateArticleResponse.isPublicCacheRefreshRequired()) {
            CacheUtils.updateCache(false, request, List.of(StaticSiteType.BLOG));
        }
        detail.setMessage(getResponseMsg(createOrUpdateArticleResponse));
        return detail;
    }

    @RequestMethod(method = HttpMethod.POST)
    public void create() throws SQLException, IOException {
        CreateArticleRequest body = getRequestBodyWithNullCheck(CreateArticleRequest.class);
        renderMissingMarkdownContent(body);
        AdminTokenVO adminToken = AdminTokenThreadLocal.getUser();
        if (shouldUseTransparentPublishStream(body)) {
            writeTransparentPublishStream(body, () -> {
                CreateOrUpdateArticleResponse create = articleService.create(adminToken, body);
                new AdminAuditService().record(request, AdminAuditAction.CREATE_ARTICLE, body.getTitle());
                return create;
            });
            return;
        }
        CreateOrUpdateArticleResponse create = articleService.create(adminToken, body);
        new AdminAuditService().record(request, AdminAuditAction.CREATE_ARTICLE, body.getTitle());
        renderArticleSaveResponse(body, create);
    }

    @RequestMethod(method = HttpMethod.POST)
    public void update() throws SQLException, IOException {
        UpdateArticleRequest body = getRequestBodyWithNullCheck(UpdateArticleRequest.class);
        renderMissingMarkdownContent(body);
        AdminTokenVO adminToken = AdminTokenThreadLocal.getUser();
        if (shouldUseTransparentPublishStream(body)) {
            writeTransparentPublishStream(body, () -> {
                CreateOrUpdateArticleResponse update = articleService.update(adminToken, body);
                if (Objects.equals(update.getRubbish(), false)) {
                    new AdminAuditService().record(request, AdminAuditAction.UPDATE_ARTICLE, body.getTitle());
                }
                return update;
            });
            return;
        }
        CreateOrUpdateArticleResponse update = articleService.update(adminToken, body);
        if (Objects.equals(update.getRubbish(), false)) {
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_ARTICLE, body.getTitle());
        }
        renderArticleSaveResponse(body, update);
    }

    private void renderArticleSaveResponse(CreateArticleRequest body, CreateOrUpdateArticleResponse saveResponse)
            throws SQLException, IOException {
        boolean transparentPublish = body.isTransparentPublish()
                && Objects.equals(saveResponse.getRubbish(), false)
                && !Objects.equals(saveResponse.getPrivacy(), true);
        AdminPageDataResponse<ArticleGlobalResponse> detail = toResponseByArticle(saveResponse, !transparentPublish);
        if (!transparentPublish) {
            response.renderJson(detail);
            return;
        }
        writeTransparentPublishStream(detail, body);
    }

    private boolean shouldUseTransparentPublishStream(CreateArticleRequest body) {
        return body.isTransparentPublish() && !body.isRubbish() && !body.isPrivacy();
    }

    private void renderMissingMarkdownContent(CreateArticleRequest body) {
        if ("markdown".equals(body.getEditorType())
                && StringUtils.isEmpty(body.getContent())
                && StringUtils.isNotEmpty(body.getMarkdown())) {
            String renderedContent = MARKDOWN_RENDERER.render(body.getMarkdown());
            if (renderedContent != null) {
                body.setContent(renderedContent);
            }
        }
    }

    private void writeTransparentPublishStream(CreateArticleRequest body, ArticleSaveTask saveTask) throws IOException {
        AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef = new AtomicReference<>();
        writeTransparentPublishStream(body, detailRef, emitter -> {
            CreateOrUpdateArticleResponse saveResponse = saveTask.save();
            AdminPageDataResponse<ArticleGlobalResponse> detail = toResponseByArticle(saveResponse, false);
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
                    PublishCheckTask publishCheckTask = startPublishCheck(detail, body);
                    if (publishCheckTask != null) {
                        publishCheckTaskRef.set(publishCheckTask);
                        emitter.send("publish-check-start", AdminSsePayloads.tool("publishCheck"));
                    }
                },
                this::updateBlogCacheWithStaticSyncNotice,
                emitter -> sendPublishCheckIfReady(publishCheckTaskRef.get(), publishCheckSent, emitter, false),
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = detailRef.get();
                    sendPublishCheckIfReady(publishCheckTaskRef.get(), publishCheckSent, emitter, true);
                    emitter.send("publish-complete", AdminSsePayloads.message(detail.getMessage()));
                }
        );
    }

    private void updateBlogCacheWithStaticSyncNotice() {
        try {
            CacheUtils.updateCacheSynchronouslyOrThrow(request, List.of(StaticSiteType.BLOG));
            recordBlogStaticSiteSync(true, "");
        } catch (RuntimeException e) {
            recordBlogStaticSiteSync(false, e.getMessage());
            throw e;
        }
    }

    private void recordBlogStaticSiteSync(boolean synced, String message) {
        if (StaticSitePlugin.isDisabled()) {
            return;
        }
        try {
            new MessageCenterOperationService().recordBlogStaticSiteSync(synced, message);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record blog static sync notice failed", e);
        }
    }

    @FunctionalInterface
    private interface ArticleSaveTask {

        CreateOrUpdateArticleResponse save() throws Exception;
    }

    @FunctionalInterface
    private interface PublishStartWriter {

        AdminPageDataResponse<ArticleGlobalResponse> write(AdminSseEmitter emitter) throws Exception;
    }

    @FunctionalInterface
    interface PublishCheckCommit {

        PublishCheckResponse commit() throws Exception;
    }

    static final class PublishCheckPersistenceGuard {

        private boolean cancelled;
        private PublishCheckResponse committedResponse;

        synchronized PublishCheckResponse commit(PublishCheckCommit commit) throws Exception {
            if (cancelled) {
                throw new CancellationException();
            }
            PublishCheckResponse response = commit.commit();
            committedResponse = response;
            return response;
        }

        synchronized PublishCheckResponse cancelOrGetCommitted() {
            if (committedResponse != null) {
                return committedResponse;
            }
            cancelled = true;
            return null;
        }
    }

    static final class PublishCheckTask {

        private final CompletableFuture<PublishCheckResponse> future;
        private final PublishCheckPersistenceGuard persistenceGuard;
        private final Long articleId;
        private final String articleTitle;

        PublishCheckTask(CompletableFuture<PublishCheckResponse> future,
                         PublishCheckPersistenceGuard persistenceGuard,
                         Long articleId, String articleTitle) {
            this.future = future;
            this.persistenceGuard = persistenceGuard;
            this.articleId = articleId;
            this.articleTitle = articleTitle;
        }
    }

    private PublishCheckTask startPublishCheck(
            AdminPageDataResponse<ArticleGlobalResponse> detail, CreateArticleRequest body) {
        if (!Objects.equals(detail.getData().getPublishCheckEnabled(), true)) {
            return null;
        }
        if (!Objects.equals(detail.getData().getAiConfigured(), true)) {
            return null;
        }
        Long articleId = Long.valueOf(detail.getData().getArticle().getLogId());
        GenerateArticleFieldRequest articleContext = new GenerateArticleFieldRequest();
        articleContext.setTitle(body.getTitle());
        articleContext.setMarkdown(StringUtils.isNotEmpty(body.getMarkdown()) ? body.getMarkdown() : body.getContent());
        articleContext.setDigest(body.getDigest());
        articleContext.setKeywords(body.getKeywords());
        fillPublishCheckContext(articleContext, body);
        PublishCheckPersistenceGuard persistenceGuard = new PublishCheckPersistenceGuard();
        CompletableFuture<PublishCheckResponse> future = CompletableFuture.supplyAsync(
                () -> buildPublishCheckPayload(articleId, articleContext, persistenceGuard));
        return new PublishCheckTask(future, persistenceGuard, articleId, articleContext.getTitle());
    }

    private void fillPublishCheckContext(GenerateArticleFieldRequest articleContext, CreateArticleRequest body) {
        if (body != null) {
            articleContext.setAlias(body.getAlias());
            articleContext.setThumbnail(body.getThumbnail());
            articleContext.setTransparentPublish(body.isTransparentPublish());
        }
        BlogWebSiteInfo blog = new WebSiteService().blogWebSiteInfo();
        articleContext.setStaticSiteEnabled(blog.getGenerator_html_status());
        articleContext.setStaticSitePluginEnabled(!StaticSitePlugin.isDisabled());
    }

    private PublishCheckResponse buildPublishCheckPayload(Long articleId, GenerateArticleFieldRequest articleContext,
                                                          PublishCheckPersistenceGuard persistenceGuard) {
        try {
            List<AIResponseEntry.AIContentEntry> aiMessages =
                    new AIChatService().runToolResponseWithoutPersistence(
                            "publish-check", articleId, "publishCheck", articleContext);
            return persistenceGuard.commit(() -> {
                if (!new WebSiteService().appendAIMessageEntries(aiMessages, articleId)) {
                    throw new AIMessageSaveException();
                }
                AIResponseEntry.AIContentEntry assistantMessage = aiMessages.get(aiMessages.size() - 1);
                Object checkPayload = assistantMessage.getPayload();
                PublishCheckResponse response = new PublishCheckResponse(
                        new PublishCheckToolPayload("publishCheck", checkPayload),
                        assistantMessage.getContent(),
                        assistantMessage.getMessageId(),
                        aiMessages);
                recordPublishCheckSuccess(articleId, articleContext.getTitle(), checkPayload);
                return response;
            });
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private void recordPublishCheckSuccess(Long articleId, String articleTitle, Object checkPayload) {
        try {
            new MessageCenterOperationService().recordPublishCheckSuccess(articleId, articleTitle, checkPayload);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check operation notice failed", e);
        }
    }

    private void recordPublishCheckError(Long articleId, String articleTitle, String message) {
        try {
            new MessageCenterOperationService().recordPublishCheckError(articleId, articleTitle, message);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check error notice failed", e);
        }
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
        if (publishCheckTask == null || (!wait && !publishCheckTask.future.isDone())) {
            return;
        }
        if (!publishCheckSent.compareAndSet(false, true)) {
            return;
        }
        try {
            PublishCheckResponse result = wait
                    ? publishCheckTask.future.get(timeoutMillis, TimeUnit.MILLISECONDS)
                    : publishCheckTask.future.join();
            emitter.send("publish-check-complete", result);
        } catch (CompletionException | ExecutionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = StringUtils.isNotEmpty(cause.getMessage())
                    ? cause.getMessage()
                    : I18nUtil.getAdminBackendStringFromRes("admin.article.publishCheck.error.failed");
            if (publishCheckTask.articleId != null) {
                recordPublishCheckError(publishCheckTask.articleId, publishCheckTask.articleTitle, message);
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
        PublishCheckResponse committedResponse = publishCheckTask.persistenceGuard.cancelOrGetCommitted();
        if (committedResponse != null) {
            emitter.send("publish-check-complete", committedResponse);
            return;
        }
        publishCheckTask.future.cancel(true);
        String message = I18nUtil.getAdminBackendStringFromRes(messageKey);
        if (publishCheckTask.articleId != null) {
            recordPublishCheckError(publishCheckTask.articleId, publishCheckTask.articleTitle, message);
        }
        emitter.send("publish-check-error", AdminSsePayloads.message(message));
    }

    @ResponseBody
    public AdminPageDataResponse<ArticlePageData> index()
            throws SQLException, ExecutionException, InterruptedException {
        String key = request.getParaToStr("key", "");
        String types = request.getParaToStr("types", "");
        String status = request.getParaToStr("status", "");
        int pageSize = request.getParaToInt("size", -1);
        if (pageSize <= 0) {
            String adminArticlePageSize = new WebSite().getStringValueByName("admin_article_page_size");
            if (StringUtils.isNotEmpty(adminArticlePageSize)) {
                pageSize = (int) Double.parseDouble(adminArticlePageSize);
            } else {
                pageSize = 10;
            }
        }
        ArticlePageData pageData = articleService.adminPage(ControllerUtil.toPageRequest(this, pageSize), key, types,
                status, request);
        return new AdminPageDataResponse<>(pageData, "", request.getUri());
    }

    @ResponseBody
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
    public AdminPageDataResponse<LoadEditArticleResponse> detail() throws SQLException {
        return new AdminPageDataResponse<>(articleService.loadDetail(getParamWithEmptyCheck("id"), request));
    }

    public void ai() throws IOException, InterruptedException, SQLException {
        String tool = request.getParaToStr("tool", "");
        GenerateArticleFieldRequest articleContext = StringUtils.isNotEmpty(tool)
                ? getRequestBodyWithNullCheck(GenerateArticleFieldRequest.class)
                : null;
        if (Objects.equals(tool, "publishCheck")) {
            fillPublishCheckContext(articleContext, null);
        }
        boolean includeArticleContext = !Objects.equals(request.getParaToStr("includeArticleContext", "true"), "false");
        AIStreamResponse streamResponse = new AIChatService().startStreamResponse(getParamWithEmptyCheck("input"),
                Long.parseLong(getParamWithEmptyCheck("id")), tool, articleContext, includeArticleContext);
        AdminSseEmitter.setHeaders(response);
        if (streamResponse.getInputStream() == null) {
            String errorPayload = new Gson().toJson(AdminSsePayloads.error(1,
                    Objects.requireNonNullElse(streamResponse.getErrorBody(), "")));
            response.write(new ByteArrayInputStream(errorPayload.getBytes(StandardCharsets.UTF_8)),
                    streamResponse.getStatusCode());
            return;
        }
        response.write(streamResponse.getInputStream(), streamResponse.getStatusCode());
    }

    @ResponseBody
    public ApiStandardResponse<List<AIResponseEntry.AIContentEntry>> appendAiContext()
            throws SQLException {
        AddArticleAIContextRequest contextRequest = getRequestBodyWithNullCheck(AddArticleAIContextRequest.class);
        List<AIResponseEntry.AIContentEntry> messages = new WebSiteService().appendArticleContextMessage(
                Long.parseLong(getParamWithEmptyCheck("id")), contextRequest);
        return new ApiStandardResponse<>(messages.stream()
                .filter(e -> !Objects.equals(e.getRole(), "system"))
                .collect(java.util.stream.Collectors.toList()));
    }

    @ResponseBody
    public ApiStandardResponse<Boolean> updateAiMessage() throws SQLException {
        UpdateAIMessageRequest updateRequest = getRequestBodyWithNullCheck(UpdateAIMessageRequest.class);
        boolean updated = new WebSiteService().updateAIMessagePayload(Long.parseLong(getParamWithEmptyCheck("id")),
                updateRequest.getMessageId(), updateRequest.getTool(), updateRequest.getPayload());
        return new ApiStandardResponse<>(updated);
    }

    @ResponseBody
    public ApiStandardResponse<Boolean> clearAiMessages() {
        boolean cleared = new WebSiteService().clearAIMessage(Long.parseLong(getParamWithEmptyCheck("id")));
        return new ApiStandardResponse<>(cleared);
    }

    @ResponseBody
    public ApiStandardResponse<ArticleAIMessageExportResponse> exportAiMessages() {
        return new ApiStandardResponse<>(
                new WebSiteService().exportAIMessage(Long.parseLong(getParamWithEmptyCheck("id"))));
    }

    @ResponseBody
    public ApiStandardResponse<UploadFileResponse> applyCover() throws SQLException {
        ApplyArticleCoverRequest coverRequest = getRequestBodyWithNullCheck(ApplyArticleCoverRequest.class);
        UploadFileResponse uploadFileResponse = new AIImageService().applyArticleCover(coverRequest, getRequest(),
                Long.parseLong(request.getParaToStr("id", "0")));
        return new ApiStandardResponse<>(uploadFileResponse);
    }

}
