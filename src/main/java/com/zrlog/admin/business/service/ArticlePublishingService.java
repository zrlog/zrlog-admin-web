package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.service.AIWritingSkillService;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.business.rest.response.PublishCheckToolPayload;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArticlePublishingService {

    private static final Logger LOGGER = LoggerUtil.getLogger(ArticlePublishingService.class);
    private static final MarkdownJsRenderer MARKDOWN_RENDERER = new MarkdownJsRenderer();

    private final AdminArticleService articleService;

    public ArticlePublishingService() {
        this(new AdminArticleService());
    }

    ArticlePublishingService(AdminArticleService articleService) {
        this.articleService = articleService;
    }

    public void prepareRequest(CreateArticleRequest request) {
        if ("markdown".equals(request.getEditorType())
                && StringUtils.isEmpty(request.getContent())
                && StringUtils.isNotEmpty(request.getMarkdown())) {
            String content = MARKDOWN_RENDERER.render(request.getMarkdown());
            if (content != null) {
                request.setContent(content);
            }
        }
    }

    public DeleteResponse deleteArticles(String ids, HttpRequest request) throws SQLException {
        boolean deleted = Arrays.stream(ids.split(",")).allMatch(id -> {
            try {
                return articleService.delete(Long.valueOf(id));
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        });
        new AdminAuditService().record(request, AdminAuditAction.DELETE_ARTICLE, ids);
        return new DeleteResponse(deleted);
    }

    public boolean shouldUseTransparentPublishStream(CreateArticleRequest request) {
        return request.isTransparentPublish() && !request.isRubbish() && !request.isPrivacy();
    }

    public boolean isTransparentPublish(CreateArticleRequest request, CreateOrUpdateArticleResponse response) {
        return request.isTransparentPublish() && Objects.equals(response.getRubbish(), false)
                && !Objects.equals(response.getPrivacy(), true);
    }

    public CreateOrUpdateArticleResponse create(AdminTokenVO token, CreateArticleRequest body, HttpRequest request)
            throws SQLException {
        CreateOrUpdateArticleResponse response = articleService.create(token, body);
        new AdminAuditService().record(request, AdminAuditAction.CREATE_ARTICLE, body.getTitle());
        return response;
    }

    public CreateOrUpdateArticleResponse update(AdminTokenVO token, UpdateArticleRequest body, HttpRequest request)
            throws SQLException {
        CreateOrUpdateArticleResponse response = articleService.update(token, body);
        if (Objects.equals(response.getRubbish(), false)) {
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_ARTICLE, body.getTitle());
        }
        return response;
    }

    public AdminPageDataResponse<ArticleGlobalResponse> articleResponse(CreateOrUpdateArticleResponse saveResponse,
                                                                        boolean refreshCache,
                                                                        HttpRequest request) throws SQLException {
        AdminPageDataResponse<ArticleGlobalResponse> detail =
                articleService.loadDetailById(saveResponse.getLogId() + "", request);
        if (refreshCache && saveResponse.isPublicCacheRefreshRequired()) {
            CacheUtils.updateCache(false, request, List.of(StaticSiteType.BLOG));
        }
        boolean saved = Objects.equals(saveResponse.getRubbish(), true)
                || Objects.equals(saveResponse.getPrivacy(), true);
        detail.setMessage(I18nUtil.getAdminBackendStringFromRes(
                saved ? "admin.article.save.success" : "admin.article.release.success"));
        return detail;
    }

    public PublishCheckTask startPublishCheck(AdminPageDataResponse<ArticleGlobalResponse> detail,
                                              CreateArticleRequest body) {
        if (!Objects.equals(detail.getData().getPublishCheckEnabled(), true)
                || !Objects.equals(detail.getData().getAiConfigured(), true)) {
            return null;
        }
        Long articleId = Long.valueOf(detail.getData().getArticle().getLogId());
        GenerateArticleFieldRequest context = publishCheckContext(body);
        PublishCheckPersistenceGuard guard = new PublishCheckPersistenceGuard();
        WebSiteService conversationStore = new WebSiteService().captureAccount();
        CompletableFuture<PublishCheckResponse> future = CompletableFuture.supplyAsync(
                () -> buildPublishCheckPayload(articleId, context, guard, conversationStore));
        return new PublishCheckTask(future, guard, articleId, context.getTitle());
    }

    public void fillPublishCheckContext(GenerateArticleFieldRequest context) {
        BlogWebSiteInfo blog = new WebSiteService().blogWebSiteInfo();
        context.setStaticSiteEnabled(blog.getGenerator_html_status());
        context.setStaticSitePluginEnabled(!StaticSitePlugin.isDisabled());
    }

    public void updateBlogCacheWithStaticSyncNotice(HttpRequest request) {
        try {
            CacheUtils.updateCacheSynchronouslyOrThrow(request, List.of(StaticSiteType.BLOG));
            recordBlogStaticSiteSync(true, "");
        } catch (RuntimeException e) {
            recordBlogStaticSiteSync(false, e.getMessage());
            throw e;
        }
    }

    public void recordPublishCheckError(Long articleId, String articleTitle, String message) {
        try {
            new MessageCenterOperationService().recordPublishCheckError(articleId, articleTitle, message);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check error notice failed", e);
        }
    }

    private GenerateArticleFieldRequest publishCheckContext(CreateArticleRequest body) {
        GenerateArticleFieldRequest context = new GenerateArticleFieldRequest();
        context.setTitle(body.getTitle());
        context.setMarkdown(StringUtils.isNotEmpty(body.getMarkdown()) ? body.getMarkdown() : body.getContent());
        context.setDigest(body.getDigest());
        context.setKeywords(body.getKeywords());
        context.setAlias(body.getAlias());
        context.setThumbnail(body.getThumbnail());
        context.setTransparentPublish(body.isTransparentPublish());
        fillPublishCheckContext(context);
        return context;
    }

    private PublishCheckResponse buildPublishCheckPayload(Long articleId, GenerateArticleFieldRequest context,
                                                          PublishCheckPersistenceGuard guard, WebSiteService conversationStore) {
        try {
            List<AIResponseEntry.AIContentEntry> messages = new AIWritingSkillService(conversationStore)
                    .runToolResponseWithoutPersistence("publish-check", articleId, "publishCheck", context);
            return guard.commit(() -> {
                if (!conversationStore.appendAIMessageEntries(messages, articleId)) {
                    throw new AIMessageSaveException();
                }
                AIResponseEntry.AIContentEntry assistant = messages.get(messages.size() - 1);
                Object payload = assistant.getPayload();
                PublishCheckResponse response = new PublishCheckResponse(
                        new PublishCheckToolPayload("publishCheck", payload), assistant.getContent(),
                        assistant.getMessageId(), messages);
                recordPublishCheckSuccess(articleId, context.getTitle(), payload);
                return response;
            });
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    public void recordPublishCheckSuccess(Long articleId, String articleTitle, Object payload) {
        try {
            new MessageCenterOperationService().recordPublishCheckSuccess(articleId, articleTitle, payload);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check operation notice failed", e);
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
    public interface PublishCheckCommit {
        PublishCheckResponse commit() throws Exception;
    }

    public static final class PublishCheckPersistenceGuard {
        private boolean cancelled;
        private PublishCheckResponse committedResponse;

        public synchronized PublishCheckResponse commit(PublishCheckCommit commit) throws Exception {
            if (cancelled) {
                throw new CancellationException();
            }
            PublishCheckResponse response = commit.commit();
            committedResponse = response;
            return response;
        }

        public synchronized PublishCheckResponse cancelOrGetCommitted() {
            if (committedResponse != null) {
                return committedResponse;
            }
            cancelled = true;
            return null;
        }
    }

    public static final class PublishCheckTask {
        private final CompletableFuture<PublishCheckResponse> future;
        private final PublishCheckPersistenceGuard persistenceGuard;
        private final Long articleId;
        private final String articleTitle;

        public PublishCheckTask(CompletableFuture<PublishCheckResponse> future,
                                PublishCheckPersistenceGuard persistenceGuard,
                                Long articleId, String articleTitle) {
            this.future = future;
            this.persistenceGuard = persistenceGuard;
            this.articleId = articleId;
            this.articleTitle = articleTitle;
        }

        public CompletableFuture<PublishCheckResponse> getFuture() {
            return future;
        }

        public PublishCheckPersistenceGuard getPersistenceGuard() {
            return persistenceGuard;
        }

        public Long getArticleId() {
            return articleId;
        }

        public String getArticleTitle() {
            return articleTitle;
        }
    }
}
