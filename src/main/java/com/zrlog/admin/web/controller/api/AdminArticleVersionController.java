package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ArticleVersionRollbackRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.ArticleVersionService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.controller.BaseController;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class AdminArticleVersionController extends BaseController {

    private final AdminArticleService articleService = new AdminArticleService();
    private final ArticleVersionService articleVersionService = new ArticleVersionService(articleService);

    private String getResponseMsg(CreateOrUpdateArticleResponse response) {
        return I18nUtil.getAdminBackendStringFromRes(Objects.equals(response.getRubbish(), true)
                || Objects.equals(response.getPrivacy(), true) ? "saveSuccess" : "releaseSuccess");
    }

    private AdminApiPageDataStandardResponse<ArticleGlobalResponse> toResponseByArticle(
            CreateOrUpdateArticleResponse createOrUpdateArticleResponse) throws SQLException {
        AdminApiPageDataStandardResponse<ArticleGlobalResponse> detail = articleService
                .loadDetailById(createOrUpdateArticleResponse.getLogId() + "", request);
        LoadEditArticleResponse loadEditArticleResponse = detail.getData().getArticle();
        if (Objects.equals(loadEditArticleResponse.isRubbish(), false)) {
            CacheUtils.updateCache(false, request, List.of(StaticSiteType.BLOG));
        }
        detail.setMessage(getResponseMsg(createOrUpdateArticleResponse));
        return detail;
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<List<ArticleVersionResponse>> index() throws SQLException {
        Integer id = Integer.valueOf(getParamWithEmptyCheck("id"));
        return new AdminApiPageDataStandardResponse<>(articleVersionService.listVersions(id));
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<ArticleVersionCompareResponse> compare() throws SQLException {
        Integer id = Integer.valueOf(getParamWithEmptyCheck("id"));
        Integer fromVersion = Integer.valueOf(getParamWithEmptyCheck("fromVersion"));
        Integer toVersion = Integer.valueOf(getParamWithEmptyCheck("toVersion"));
        return new AdminApiPageDataStandardResponse<>(articleVersionService.compare(id, fromVersion, toVersion));
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<ArticleGlobalResponse> rollback() throws SQLException {
        ArticleVersionRollbackRequest body = getRequestBodyWithNullCheck(ArticleVersionRollbackRequest.class);
        CreateOrUpdateArticleResponse response = articleVersionService.rollback(AdminTokenThreadLocal.getUser(), body);
        new com.zrlog.admin.business.service.AdminAuditService().record(request,
                "回滚文章版本：" + body.getLogId() + " -> v" + body.getTargetVersion(), "article");
        return toResponseByArticle(response);
    }
}
