package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.base.BasicWebSiteInfo;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.base.ContentProtectorWebSiteInfo;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.base.OtherWebSiteInfo;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.UpgradeSettingsResponse;
import com.zrlog.admin.business.rest.response.VersionResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.UpgradeSettingsService;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Map;

public class WebSiteController extends BaseController {

    private final AdminPageDataFactory pages;

    private final WebSiteService webSiteService;
    private final WebSiteSettingsService settingsService;
    private final UpgradeSettingsService upgradeSettings;
    private final FeatureLabService features;

    @Inject
    public WebSiteController(
            WebSiteSettingsService settingsService,
            UpgradeSettingsService upgradeSettings,
            FeatureLabService features,
            AdminPageDataFactory pages,
            WebSiteService webSiteService) {
        this.webSiteService = webSiteService;

        this.pages = pages;
        this.upgradeSettings = upgradeSettings;
        this.settingsService = settingsService;
        this.features = features;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.version")
    public AdminPageDataResponse<VersionResponse> version() {
        VersionResponse version = settingsService.version(getCurrentChangeLog(I18nUtil.getBackend()));
        return pages.response(version, "", AdminConstants.ADMIN_URI_BASE_PATH + "/system/version");
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
        AdminWebSiteInfo settings = webSiteService.adminWebSiteInfo();
        if (BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            settings.setBackend_server_url(null);
        }
        return page(settings);
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
            FeatureLabWebSiteInfo settings = getRequestBodyWithNullCheck(FeatureLabWebSiteInfo.class);
            settingsService.update(settings, request);
            features.updateWebhook(settings.getFeature_webhook_enabled());
        }
        return page(features.featureLab());
    }

    @RequestLock(onlyOnPostMethod = true)
    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.upgrade")
    public AdminPageDataResponse<UpgradeSettingsResponse> upgrade() throws SQLException {
        UpgradeSettingsService upgradeSettings = upgradeSettingsService();
        if (isPost()) {
            upgradeSettings.save(getRequestBodyWithNullCheck(UpgradeSettingsRequest.class), request);
        }
        return page(upgradeSettings.read(isPost() ? null : request.getParaToStr("channel")));
    }

    protected UpgradeSettingsService upgradeSettingsService() { return upgradeSettings; }

    private boolean isPost() {
        return request.getMethod() == HttpMethod.POST;
    }

    private <T> AdminPageDataResponse<T> page(T data) {
        String message = isPost() ? I18nUtil.getAdminBackendStringFromRes("admin.common.update.success") : "";
        return pages.response(data, message, request.getUri());
    }
}
