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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArticlePublishingService {

    private static final Logger LOGGER = LoggerUtil.getLogger(ArticlePublishingService.class);

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
            String content = renderMarkdown(request.getMarkdown());
            if (content != null) {
                request.setContent(content);
            }
        }
    }

    private String renderMarkdown(String markdown) {
        try {
            return NativeMarkdown.RENDERER.render(markdown);
        } catch (NoClassDefFoundError e) {
            // Ordinary JDK ZIP/WAR omit the renderer; keep the supplied content.
            LOGGER.log(Level.FINE, "Server-side Markdown renderer is not included", e);
            return null;
        }
    }

    // Keep the optional renderer out of the publishing service's class initialization.
    private static final class NativeMarkdown {
        private static final MarkdownJsRenderer RENDERER = new MarkdownJsRenderer();
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
            // The article is already saved. Refresh public data now, but do not hold
            // the editor's acknowledgement while plugins regenerate the static site.
            CacheUtils.updateCache(true, request, List.of(StaticSiteType.BLOG));
        }
        boolean saved = Objects.equals(saveResponse.getRubbish(), true)
                || Objects.equals(saveResponse.getPrivacy(), true);
        detail.setMessage(I18nUtil.getAdminBackendStringFromRes(
                saved ? "admin.article.save.success" : "admin.article.release.success"));
        return detail;
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

}
