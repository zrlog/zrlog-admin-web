package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.ArticleQueryService;
import com.zrlog.admin.business.service.ArticleRevisionService;
import com.zrlog.admin.business.service.ArticleVersionService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class AdminArticleVersionController extends BaseController {
    private final AdminAuditService adminAuditService;
    private final AdminRequestIdentity requestIdentity;

    private final ArticleQueryService articleService;
    private final CacheRefreshService cacheRefresh;
    private final ArticleVersionService articleVersionService;
    private final ArticleRevisionService revisions;

    @Inject
    public AdminArticleVersionController(
            ArticleQueryService articleService,
            CacheRefreshService cacheRefresh,
            AdminAuditService adminAuditService,
            ArticleVersionService articleVersionService,
            ArticleRevisionService revisions,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;
        this.articleVersionService = articleVersionService;
        this.revisions = revisions;

        this.articleService = articleService;
        this.cacheRefresh = cacheRefresh;
    }

    private String getResponseMsg(CreateOrUpdateArticleResponse response) {
        return I18nUtil.getAdminBackendStringFromRes(Objects.equals(response.getRubbish(), true)
                || Objects.equals(response.getPrivacy(), true) ? "admin.article.save.success" : "admin.article.release.success");
    }

    private AdminPageDataResponse<ArticleGlobalResponse> toResponseByArticle(
            CreateOrUpdateArticleResponse createOrUpdateArticleResponse) throws SQLException {
        AdminPageDataResponse<ArticleGlobalResponse> detail = articleService
                .loadDetailById(requestIdentity.current(), createOrUpdateArticleResponse.getLogId() + "", request);
        if (createOrUpdateArticleResponse.isPublicCacheRefreshRequired()) {
            cacheRefresh.updateCache(false, request, List.of(StaticSiteType.BLOG));
        }
        detail.setMessage(getResponseMsg(createOrUpdateArticleResponse));
        return detail;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, descriptionKey = "article.versions")
    public ApiStandardResponse<List<ArticleVersionResponse>> index() throws SQLException {
        Integer id = Integer.valueOf(getParamWithEmptyCheck("id"));
        return new ApiStandardResponse<>(articleVersionService.listVersions(requestIdentity.current(), id));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_READ, articleQuery = true, descriptionKey = "article.compareVersions")
    public ApiStandardResponse<ArticleVersionCompareResponse> compare() throws SQLException {
        Integer id = Integer.valueOf(getParamWithEmptyCheck("id"));
        Integer fromVersion = Integer.valueOf(getParamWithEmptyCheck("fromVersion"));
        Integer toVersion = Integer.valueOf(getParamWithEmptyCheck("toVersion"));
        return new ApiStandardResponse<>(revisions.compare(requestIdentity.current(), id, fromVersion, toVersion, request));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ARTICLE_UPDATE, conditional = AccountAction.ARTICLE_PUBLISH, descriptionKey = "article.restoreVersion")
    public AdminPageDataResponse<ArticleGlobalResponse> rollback() throws SQLException {
        ArticleVersionRollbackRequest body = getRequestBodyWithNullCheck(ArticleVersionRollbackRequest.class);
        CreateOrUpdateArticleResponse response =
                revisions.rollback(requestIdentity.current(), body, request);
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                AdminAuditAction.ROLLBACK_ARTICLE_VERSION,
                body.getLogId() + " -> v" + body.getTargetVersion());
        return toResponseByArticle(response);
    }
}
