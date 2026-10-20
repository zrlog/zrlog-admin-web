package com.zrlog.admin.business.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
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
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.exception.DAOException;
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
    private final AdminAuditService adminAuditService;
    private final MessageCenterOperationService messageCenterOperationService;

    private static final Logger LOGGER = LoggerUtil.getLogger(ArticlePublishingService.class);

    private final AdminArticleService articleService;
    private final ArticleQueryService queries;

    private final CacheRefreshService cacheRefresh;
    private final MarkdownRenderer markdownRenderer;

    @Inject
    public ArticlePublishingService(
            AdminArticleService articleService,
            ArticleQueryService queries,
            CacheRefreshService cacheRefresh,
            MarkdownRenderer markdownRenderer,
            AdminAuditService adminAuditService,
            MessageCenterOperationService messageCenterOperationService) {
        this.adminAuditService = adminAuditService;
        this.messageCenterOperationService = messageCenterOperationService;

        this.articleService = articleService;
        this.queries = queries;
        this.cacheRefresh = cacheRefresh;
        this.markdownRenderer = Objects.requireNonNull(markdownRenderer);
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

    public DeleteResponse deleteArticles(String ids, HttpRequest request) throws SQLException {
        boolean deleted =
                Arrays.stream(ids.split(","))
                        .allMatch(
                                id -> {
                                    try {
                                        boolean removed = articleService.delete(Long.valueOf(id));
                                        if (removed) {
                                            this.adminAuditService.record(
                                                    request, AdminAuditAction.DELETE_ARTICLE, id);
                                        }
                                        return removed;
                                    } catch (SQLException e) {
                                        throw new DAOException(e);
                                    }
                                });
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
        this.adminAuditService.record(request, AdminAuditAction.CREATE_ARTICLE, body.getTitle());
        recordPublication(body, response, request);
        return response;
    }

    public CreateOrUpdateArticleResponse update(AdminTokenVO token, UpdateArticleRequest body, HttpRequest request)
            throws SQLException {
        CreateOrUpdateArticleResponse response = articleService.update(token, body);
        recordPublication(body, response, request);
        return response;
    }

    private void recordPublication(CreateArticleRequest body, CreateOrUpdateArticleResponse saved, HttpRequest request) {
        // Admin APIs, MCP and the built-in assistant share the same publication policy.
        if (Objects.equals(saved.getRubbish(), false) && Objects.equals(saved.getPrivacy(), false)) {
            this.adminAuditService.record(
                    request, AdminAuditAction.PUBLISH_ARTICLE, body.getTitle());
        }
    }

    public AdminPageDataResponse<ArticleGlobalResponse> articleResponse(CreateOrUpdateArticleResponse saveResponse,
                                                                        boolean refreshCache,
                                                                        HttpRequest request) throws SQLException {
        AdminPageDataResponse<ArticleGlobalResponse> detail =
                queries.loadDetailById(saveResponse.getLogId() + "", request);
        if (refreshCache && saveResponse.isPublicCacheRefreshRequired()) {
            // The article is already saved. Refresh public data now, but do not hold
            // the editor's acknowledgement while plugins regenerate the static site.
            cacheRefresh.updateCache(true, request, List.of(StaticSiteType.BLOG));
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
