package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.dao.dto.Direction;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.admin.business.rest.base.UserPreferences;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.ZrLogUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class AdminArticleController extends BaseController {

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
        AtomicReference<ArticleAssistant.PublishProgress> progressRef =
                new AtomicReference<>(ArticleAssistant.PublishProgress.NONE);
        AdminStaticSiteSsePublisher.write(
                response,
                "transparent-publish",
                "publish-error",
                "static-error",
                siteTypes,
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = publishStartWriter.write(emitter);
                    progressRef.set(ArticleAssistant.current().beginPublishCheck(detail.getData(), body, emitter));
                },
                () -> publishingService.updateBlogCacheWithStaticSyncNotice(request),
                emitter -> progressRef.get().progress(emitter),
                emitter -> {
                    AdminPageDataResponse<ArticleGlobalResponse> detail = detailRef.get();
                    progressRef.get().complete(emitter);
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

    @ResponseBody
    @RequiresAction(value = AccountAction.ARTICLE_READ, descriptionKey = "article.list")
    public AdminPageDataResponse<ArticlePageData> index()
            throws SQLException, ExecutionException, InterruptedException {
        String key = request.getParaToStr("key", "");
        String types = request.getParaToStr("types", "");
        UserPreferences.ArticleList preferences = new UserPreferenceService().effective().articleList;
        String status = request.decodeParamMap().containsKey("status") ? request.getParaToStr("status", "") : preferences.status;
        int pageSize = articleService.resolveAdminPageSize(request.getParaToInt("size", -1));
        PageRequest requested = ControllerUtil.toPageRequest(this, pageSize);
        PageRequestImpl page = new PageRequestImpl(requested.getPage(), requested.getSize());
        page.setOrders(requested.getSorts());
        if (!request.decodeParamMap().containsKey("sort") && !request.decodeParamMap().containsKey("sidx")) {
            String[] sort = preferences.sort.split(",");
            page.setOrders(java.util.List.of(new OrderBy(sort[0], Direction.valueOf(sort[1]))));
        }
        ArticlePageData pageData = articleService.adminPage(page, key, types, status, request);
        pageData.setColumns(preferences.columns);
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

}
