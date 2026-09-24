package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.ai.service.AIToolService;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.base.BasicWebSiteInfo;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.base.ContentProtectorWebSiteInfo;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.base.OtherWebSiteInfo;
import com.zrlog.admin.business.rest.request.OptimizeAiPromptRequest;
import com.zrlog.admin.business.rest.request.OptimizeWebsiteDescriptionRequest;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.OptimizeAiPromptResponse;
import com.zrlog.admin.business.rest.response.OptimizeWebsiteDescriptionResponse;
import com.zrlog.admin.business.rest.response.VersionResponse;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class WebSiteController extends BaseController {

    private final WebSiteService webSiteService = new WebSiteService();
    private final WebSiteSettingsService settingsService = new WebSiteSettingsService();

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.version")
    public AdminPageDataResponse<VersionResponse> version() {
        VersionResponse version = settingsService.version(getCurrentChangeLog(I18nUtil.getBackend()));
        return new AdminPageDataResponse<>(version, "", request.getUri());
    }

    protected String getCurrentChangeLog(Map<String, Object> backendMessages) {
        return UpdateVersionInfoPlugin.getCurrentChangeLog(backendMessages);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.basic")
    public AdminPageDataResponse<BasicWebSiteInfo> index() throws SQLException {
        return basic();
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = {StaticSiteType.BLOG, StaticSiteType.ADMIN})
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.basic")
    public AdminPageDataResponse<BasicWebSiteInfo> basic() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(BasicWebSiteInfo.class), request);
        }
        return page(webSiteService.basicWebSiteInfo());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.optimizeDescription")
    public ApiStandardResponse<OptimizeWebsiteDescriptionResponse> optimizeDescription()
            throws IOException, InterruptedException {
        OptimizeWebsiteDescriptionRequest body = getRequestBodyWithNullCheck(OptimizeWebsiteDescriptionRequest.class);
        return new ApiStandardResponse<>(new AIToolService().optimizeWebsiteDescription(body));
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.optimizePrompt")
    public ApiStandardResponse<OptimizeAiPromptResponse> optimizeAiPrompt()
            throws IOException, InterruptedException {
        OptimizeAiPromptRequest body = getRequestBodyWithNullCheck(OptimizeAiPromptRequest.class);
        return new ApiStandardResponse<>(new AIToolService().optimizeAiPrompt(body));
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.blog")
    public AdminPageDataResponse<BlogWebSiteInfo> blog() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(BlogWebSiteInfo.class), request);
        }
        return page(webSiteService.blogWebSiteInfo());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.other")
    public AdminPageDataResponse<OtherWebSiteInfo> other() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(OtherWebSiteInfo.class), request);
        }
        return page(webSiteService.other());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.ADMIN)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.admin")
    public AdminPageDataResponse<AdminWebSiteInfo> admin() throws SQLException {
        if (isPost()) {
            settingsService.updateAdmin(getRequestBodyWithNullCheck(AdminWebSiteInfo.class), request);
        }
        return page(webSiteService.adminWebSiteInfo());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.editor")
    public AdminPageDataResponse<ArticleEditWebSiteInfo> articleEdit() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(ArticleEditWebSiteInfo.class), request);
        }
        return page(webSiteService.articleEditWebSiteInfo());
    }

    @RefreshCache(onlyOnPostMethod = true, updateStaticSites = StaticSiteType.BLOG)
    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.protection")
    public AdminPageDataResponse<ContentProtectorWebSiteInfo> contentProtector() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(ContentProtectorWebSiteInfo.class), request);
        }
        return page(webSiteService.contentProtector());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.lab")
    public AdminPageDataResponse<FeatureLabWebSiteInfo> lab() throws SQLException {
        if (isPost()) {
            settingsService.update(getRequestBodyWithNullCheck(FeatureLabWebSiteInfo.class), request);
        }
        return page(webSiteService.featureLab());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.upgrade")
    public AdminPageDataResponse<UpgradeWebSiteInfo> upgrade() throws SQLException {
        if (isPost()) {
            settingsService.updateUpgrade(getRequestBodyWithNullCheck(UpgradeWebSiteInfo.class), request);
        }
        return page(webSiteService.upgradeWebSiteInfo());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.ai")
    public AdminPageDataResponse<AIWebSiteInfoResponse> ai() throws SQLException {
        if (isPost()) {
            settingsService.updateAi(getRequestBodyWithNullCheck(AIWebSiteInfo.class), request);
        }
        return page(settingsService.aiResponse());
    }

    private boolean isPost() {
        return request.getMethod() == HttpMethod.POST;
    }

    private <T> AdminPageDataResponse<T> page(T data) {
        String message = isPost() ? I18nUtil.getAdminBackendStringFromRes("admin.common.update.success") : "";
        return new AdminPageDataResponse<>(data, message, request.getUri());
    }
}
