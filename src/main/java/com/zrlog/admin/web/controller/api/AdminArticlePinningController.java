package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ArticlePinningRequest;
import com.zrlog.admin.business.rest.request.MoveArticlePinningRequest;
import com.zrlog.admin.business.rest.response.ArticlePinningResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.ArticlePinningService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;

public class AdminArticlePinningController extends BaseController {

    private final ArticlePinningService pinningService = new ArticlePinningService();

    @ResponseBody
    public ApiStandardResponse<ArticlePinningResponse> index() throws SQLException {
        return new ApiStandardResponse<>(pinningService.list());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    public ApiStandardResponse<ArticlePinningResponse> pin() throws SQLException {
        ArticlePinningRequest body = getRequestBodyWithNullCheck(ArticlePinningRequest.class);
        ArticlePinningResponse result = pinningService.pin(body.getLogId());
        record("pin", body.getLogId());
        return response(result, "admin.article.pinning.pin.success");
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    public ApiStandardResponse<ArticlePinningResponse> unpin() throws SQLException {
        ArticlePinningRequest body = getRequestBodyWithNullCheck(ArticlePinningRequest.class);
        ArticlePinningResponse result = pinningService.unpin(body.getLogId());
        record("unpin", body.getLogId());
        return response(result, "admin.article.pinning.unpin.success");
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    public ApiStandardResponse<ArticlePinningResponse> move() throws SQLException {
        MoveArticlePinningRequest body = getRequestBodyWithNullCheck(MoveArticlePinningRequest.class);
        ArticlePinningResponse result = pinningService.move(body.getLogId(), body.getDirection());
        record("move-" + body.getDirection(), body.getLogId());
        return response(result, "admin.article.pinning.move.success");
    }

    private ApiStandardResponse<ArticlePinningResponse> response(ArticlePinningResponse result, String messageKey) {
        return new ApiStandardResponse<>(result, I18nUtil.getAdminBackendStringFromRes(messageKey));
    }

    private void record(String operation, Long logId) {
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_ARTICLE_PINNING,
                operation + ": " + logId);
    }
}
