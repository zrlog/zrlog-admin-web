package com.zrlog.admin.web.controller.api;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminArticleController extends BaseController {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminArticleController.class);

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
        LoadEditArticleResponse loadEditArticleResponse = detail.getData().getArticle();
        // 为发布状态才需要更新缓存信息（避免无用更新）
        if (refreshCache && Objects.equals(loadEditArticleResponse.isRubbish(), false)) {
            CacheUtils.updateCache(false, request, List.of(StaticSiteType.BLOG));
        }
        detail.setMessage(getResponseMsg(createOrUpdateArticleResponse));
        return detail;
    }

    public void create() throws SQLException, IOException {
        CreateArticleRequest body = getRequestBodyWithNullCheck(CreateArticleRequest.class);
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

    public void update() throws SQLException, IOException {
        UpdateArticleRequest body = getRequestBodyWithNullCheck(UpdateArticleRequest.class);
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

    private void writeTransparentPublishStream(CreateArticleRequest body, ArticleSaveTask saveTask) throws IOException {
        AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef = new AtomicReference<>();
        writeTransparentPublishStream(body, detailRef, emitter -> {
            CreateOrUpdateArticleResponse saveResponse = saveTask.save();
            AdminPageDataResponse<ArticleGlobalResponse> detail = toResponseByArticle(saveResponse, false);
            detailRef.set(detail);
            emitter.send("article", detail);
            emitter.send("publish-start", Map.of("message", detail.getMessage()));
            return detail;
        });
    }

    private void writeTransparentPublishStream(AdminPageDataResponse<ArticleGlobalResponse> detail,
                                               CreateArticleRequest body)
            throws IOException {
        AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef = new AtomicReference<>(detail);
        writeTransparentPublishStream(body, detailRef, emitter -> {
            emitter.send("article", detail);
            emitter.send("publish-start", Map.of("message", detail.getMessage()));
            return detail;
        });
    }

    private void writeTransparentPublishStream(CreateArticleRequest body,
                                               AtomicReference<AdminPageDataResponse<ArticleGlobalResponse>> detailRef,
                                               PublishStartWriter publishStartWriter)
            throws IOException {
        List<StaticSiteType> siteTypes = List.of(StaticSiteType.BLOG);
        AtomicReference<CompletableFuture<PublishCheckResponse>> publishCheckFutureRef = new AtomicReference<>();
        AtomicBoolean publishCheckSent = new AtomicBoolean(false);
        AdminStaticSiteSsePublisher.write(
                response,
                "transparent-publish",
                "publish-error",
                siteTypes,
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = publishStartWriter.write(emitter);
                    CompletableFuture<PublishCheckResponse> publishCheckFuture = startPublishCheck(detail, body);
                    if (publishCheckFuture != null) {
                        publishCheckFutureRef.set(publishCheckFuture);
                        emitter.send("publish-check-start", Map.of("tool", "publishCheck"));
                    }
                },
                this::updateBlogCacheWithStaticSyncNotice,
                emitter -> sendPublishCheckIfReady(publishCheckFutureRef.get(), publishCheckSent, emitter, false),
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = detailRef.get();
                    emitter.send("publish-complete", Map.of("message", detail.getMessage()));
                    sendPublishCheckIfReady(publishCheckFutureRef.get(), publishCheckSent, emitter, true);
                }
        );
    }

    private void updateBlogCacheWithStaticSyncNotice() {
        try {
            CacheUtils.updateCache(false, request, List.of(StaticSiteType.BLOG));
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

    private CompletableFuture<PublishCheckResponse> startPublishCheck(
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
        return CompletableFuture.supplyAsync(() -> buildPublishCheckPayload(articleId, articleContext));
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

    private PublishCheckResponse buildPublishCheckPayload(Long articleId, GenerateArticleFieldRequest articleContext) {
        try {
            List<AIResponseEntry.AIContentEntry> aiMessages =
                    new AIChatService().runToolResponse("publish-check", articleId, "publishCheck", articleContext);
            AIResponseEntry.AIContentEntry assistantMessage = aiMessages.get(aiMessages.size() - 1);
            Object checkPayload = assistantMessage.getPayload();
            recordPublishCheckSuccess(articleId, articleContext.getTitle(), checkPayload);
            return new PublishCheckResponse(
                    new PublishCheckToolPayload("publishCheck", checkPayload),
                    assistantMessage.getContent(),
                    assistantMessage.getMessageId(),
                    aiMessages);
        } catch (Exception e) {
            recordPublishCheckError(articleId, articleContext.getTitle(), Objects.requireNonNullElse(e.getMessage(), ""));
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

    private void sendPublishCheckIfReady(CompletableFuture<PublishCheckResponse> publishCheckFuture,
                                         AtomicBoolean publishCheckSent, AdminSseEmitter emitter, boolean wait)
            throws Exception {
        if (publishCheckFuture == null || publishCheckSent.get() || (!wait && !publishCheckFuture.isDone())) {
            return;
        }
        try {
            emitter.send("publish-check-complete", publishCheckFuture.join());
        } catch (CompletionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            emitter.send("publish-check-error", Map.of("message", Objects.requireNonNullElse(cause.getMessage(), "")));
        } finally {
            publishCheckSent.set(true);
        }
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
            String errorPayload = new Gson().toJson(Map.of("error", 1, "message",
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
