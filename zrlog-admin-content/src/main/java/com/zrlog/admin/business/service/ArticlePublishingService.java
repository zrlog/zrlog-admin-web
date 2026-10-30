package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.MarkdownRenderer;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class ArticlePublishingService {
    private static final Logger LOGGER = LoggerUtil.getLogger(ArticlePublishingService.class);
    private final AdminAuditService adminAuditService;
    private final MessageCenterOperationService messageCenterOperationService;
    private final AdminArticleService articleService;
    private final ArticleQueryService queries;
    private final java.util.function.Supplier<com.zrlog.admin.business.content.ArticlePublicationScheduler> scheduler;
    private final AccountPermissionService permissions;


    private final CacheRefreshService cacheRefresh;
    private final MarkdownRenderer markdownRenderer;

    @Inject
    public ArticlePublishingService(
            AdminArticleService articleService,
            ArticleQueryService queries,
            CacheRefreshService cacheRefresh,
            MarkdownRenderer markdownRenderer,
            AdminAuditService adminAuditService,
            MessageCenterOperationService messageCenterOperationService,
            java.util.function.Supplier<com.zrlog.admin.business.content.ArticlePublicationScheduler> scheduler,
            AccountPermissionService permissions) {
        this.scheduler = scheduler;
        this.permissions = permissions;
        this.adminAuditService = adminAuditService;
        this.messageCenterOperationService = messageCenterOperationService;

        this.articleService = articleService;
        this.queries = queries;
        this.cacheRefresh = cacheRefresh;
        this.markdownRenderer = Objects.requireNonNull(markdownRenderer);
    }

    // Standalone content consumers can publish immediately without installing the host scheduler.
    public ArticlePublishingService(AdminArticleService articles, ArticleQueryService queries, CacheRefreshService cache,
                                    MarkdownRenderer markdown, AdminAuditService audit, MessageCenterOperationService messages) {
        this(articles, queries, cache, markdown, audit, messages, () -> null, null);
    }

    private boolean scheduled(CreateArticleRequest body) {
        return body.getPublishAt() != null && body.getPublishAt() > System.currentTimeMillis();
    }

    private boolean preparePublication(AccountAccess actor, CreateArticleRequest body) throws SQLException {
        if (!scheduled(body)) return false;
        AccountPermissionService.requireActions(actor, List.of("article.read", "article.update", "article.publish"));
        if (body.isRubbish() || body.isPrivacy() || scheduler.get() == null) throw new com.zrlog.common.exception.ArgsException("publishAt");
        if (body instanceof UpdateArticleRequest) {
            var current = permissions.article(actor, ((UpdateArticleRequest) body).getLogId(), true);
            if (!AccountAccess.truth(current.get("rubbish"))) throw new com.zrlog.common.exception.ArgsException("publishAt");
        }
        body.setRubbish(true);
        return true;
    }

    private void schedulePublication(AccountAccess actor, CreateArticleRequest body, CreateOrUpdateArticleResponse saved,
                                     AdminAuditContext audit, boolean scheduled) {
        if (!scheduled) {
            if (!Boolean.TRUE.equals(saved.getRubbish()) && !Boolean.TRUE.equals(saved.getPrivacy()) && scheduler.get() != null) {
                try { scheduler.get().cancel(actor, saved.getLogId(), audit); }
                catch (Exception failure) { LOGGER.log(Level.WARNING, "Could not pause the previous publication plan for article " + saved.getLogId(), failure); }
            }
            return;
        }
        try {
            saved.setPublicationSchedule(scheduler.get().schedule(actor, saved.getLogId(), saved.getVersion(), body.getTitle(), body.getPublishAt(), audit));
        } catch (Exception failure) {
            // The draft is durable already. Return its real ID/version so retrying cannot duplicate the article.
            var result = new com.zrlog.admin.business.content.ArticlePublicationScheduler.Schedule();
            result.state = "failed"; result.publishAt = body.getPublishAt(); result.articleVersion = saved.getVersion();
            saved.setPublicationSchedule(result);
            LOGGER.log(Level.WARNING, "Publication schedule failed for saved draft " + saved.getLogId(), failure);
        }
    }

    public void prepareRequest(CreateArticleRequest request) {
        if ("markdown".equals(request.getEditorType())
                && StringUtils.isEmpty(request.getContent())
                && StringUtils.isNotEmpty(request.getMarkdown())) {
            String content = markdownRenderer.render(request.getMarkdown());
            if (content != null) {
                request.setContent(content);
            }
        }
    }

    public DeleteResponse deleteArticles(AccountAccess actor, String ids, AdminAuditContext audit) throws SQLException {
        boolean deleted =
                Arrays.stream(ids.split(","))
                        .allMatch(
                                id -> {
                                    try {
                                        boolean removed = articleService.delete(actor, Long.valueOf(id));
                                        if (removed) {
                                            this.adminAuditService.record(audit, AdminAuditAction.DELETE_ARTICLE, id);
                                        }
                                        return removed;
                                    } catch (SQLException e) {
                                        throw new DAOException(e);
                                    }
                                });
        return new DeleteResponse(deleted);
    }

    public boolean shouldUseTransparentPublishStream(CreateArticleRequest request) {
        return !scheduled(request) && request.isTransparentPublish() && !request.isRubbish() && !request.isPrivacy();
    }

    public boolean isTransparentPublish(CreateArticleRequest request, CreateOrUpdateArticleResponse response) {
        return request.isTransparentPublish() && Objects.equals(response.getRubbish(), false)
                && !Objects.equals(response.getPrivacy(), true);
    }

    public CreateOrUpdateArticleResponse create(AccountAccess actor, CreateArticleRequest body, AdminAuditContext audit)
            throws SQLException {
        boolean scheduled = preparePublication(actor, body);
        CreateOrUpdateArticleResponse response = articleService.create(actor, body);
        schedulePublication(actor, body, response, audit, scheduled);
        this.adminAuditService.record(audit, AdminAuditAction.CREATE_ARTICLE, body.getTitle());
        recordPublication(body, response, audit);
        return response;
    }

    public CreateOrUpdateArticleResponse update(AccountAccess actor, UpdateArticleRequest body, AdminAuditContext audit)
            throws SQLException {
        boolean scheduled = preparePublication(actor, body);
        CreateOrUpdateArticleResponse response = articleService.update(actor, body);
        schedulePublication(actor, body, response, audit, scheduled);
        recordPublication(body, response, audit);
        return response;
    }

    private void recordPublication(CreateArticleRequest body, CreateOrUpdateArticleResponse saved, AdminAuditContext audit) {
        // Admin APIs, MCP and the built-in assistant share the same publication policy.
        if (Objects.equals(saved.getRubbish(), false) && Objects.equals(saved.getPrivacy(), false)) {
            this.adminAuditService.record(audit, AdminAuditAction.PUBLISH_ARTICLE, body.getTitle());
        }
    }

    public AdminPageDataResponse<ArticleGlobalResponse> articleResponse(AccountAccess actor, CreateOrUpdateArticleResponse saveResponse,
                                                                        boolean refreshCache,
                                                                        HttpRequest request) throws SQLException {
        AdminPageDataResponse<ArticleGlobalResponse> detail =
                queries.loadDetailById(actor, saveResponse.getLogId() + "", request);
        if (refreshCache && saveResponse.isPublicCacheRefreshRequired()) {
            // The article is already saved. Refresh public data now, but do not hold
            // the editor's acknowledgement while plugins regenerate the static site.
            cacheRefresh.updateCache(true, request, List.of(StaticSiteType.BLOG));
        }
        detail.getData().setPublicationSchedule(saveResponse.getPublicationSchedule());
        if (saveResponse.getPublicationSchedule() != null) {
            detail.setMessage(I18nUtil.getAdminBackendStringFromRes("scheduled".equals(saveResponse.getPublicationSchedule().state)
                    ? "admin.automation.publish.scheduled" : "admin.automation.publish.scheduleFailed"));
            return detail;
        }
        boolean saved = Objects.equals(saveResponse.getRubbish(), true)
                || Objects.equals(saveResponse.getPrivacy(), true);
        detail.setMessage(I18nUtil.getAdminBackendStringFromRes(
                saved ? "admin.article.save.success" : "admin.article.release.success"));
        return detail;
    }

    public void updateBlogCacheWithStaticSyncNotice(HttpRequest request) {
        try {
            cacheRefresh.updateCacheSynchronouslyOrThrow(request, List.of(StaticSiteType.BLOG));
            recordBlogStaticSiteSync(true, "");
        } catch (RuntimeException e) {
            recordBlogStaticSiteSync(false, e.getMessage());
            throw e;
        }
    }

    private void recordBlogStaticSiteSync(boolean synced, String message) {
        if (cacheRefresh.isStaticSiteDisabled()) {
            return;
        }
        try {
            this.messageCenterOperationService.recordBlogStaticSiteSync(synced, message);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record blog static sync notice failed", e);
        }
    }

}
