package com.zrlog.admin.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminDashboardService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminResourceProvider;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.business.service.AdminStatisticsService;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.MessageCenterService;
import com.zrlog.admin.business.service.TemplateService;
import com.zrlog.admin.business.service.UpgradeSettingsService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.util.AdminNativeImageUtils;
import com.zrlog.admin.util.AdminStaticSiteProgress;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.util.SystemInfoUtils;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.controller.api.AdminController;
import com.zrlog.admin.web.controller.api.AdminDevController;
import com.zrlog.admin.web.controller.api.AdminProxyController;
import com.zrlog.admin.web.controller.api.AdminPublicController;
import com.zrlog.admin.web.controller.api.AdminSystemController;
import com.zrlog.admin.web.controller.api.MessageCenterController;
import com.zrlog.admin.web.controller.api.PluginUploadController;
import com.zrlog.admin.web.controller.api.TemplateController;
import com.zrlog.admin.web.controller.api.UpgradeController;
import com.zrlog.admin.web.controller.api.WebSiteController;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.blog.web.plugin.TemplateDownloadPlugin;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.service.UpgradeService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.TokenServiceProvider;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.List;

public class AdminWebSetup implements WebSetup, TokenServiceProvider {

    private final ZrLogConfig zrLogConfig;
    private final AdminTokenService sessionTokens;

    public AdminWebSetup(ZrLogConfig zrLogConfig) {
        this.zrLogConfig = zrLogConfig;
        this.sessionTokens = new AdminTokenService(
                WebSiteUtils.DEFAULT_SESSION_TIMEOUT / 60000, zrLogConfig::isInstalled, this::staticHtmlEnabled);
        if (zrLogConfig.getServerConfig().isNativeImageAgent()) {
            //register
            AdminNativeImageUtils.reg();
        }
    }

    private boolean staticHtmlEnabled() {
        WebSiteInfoProvider provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
        var site = provider == null ? null : provider.getPublicWebSiteInfo();
        return site != null && Boolean.TRUE.equals(site.getGenerator_html_status());
    }

    private AdminResourceInfoResponse.Capabilities capabilities() {
        var capabilities = new AdminResourceInfoResponse.Capabilities();
        capabilities.account = zrLogConfig.getWebSetup(AccountWebSetup.class) != null;
        capabilities.content = zrLogConfig.getWebSetup(ContentWebSetup.class) != null;
        capabilities.assets = zrLogConfig.getWebSetup(AssetsWebSetup.class) != null;
        capabilities.ai = zrLogConfig.getWebSetup(AIWebSetup.class) != null;
        capabilities.access = zrLogConfig.getWebSetup(AccessWebSetup.class) != null;
        capabilities.mcp = zrLogConfig.getWebSetup(McpWebSetup.class) != null;
        return capabilities;
    }

    @Override
    public void setup() {
        zrLogConfig.getServerConfig().getBindings()
                .controller(AdminDevController.class, (r, request, response) -> new AdminDevController(r.get(AdminPageDataFactory.class)))
                .controller(AdminProxyController.class, (r, request, response) -> new AdminProxyController(r.get(AdminPageDataFactory.class)))
                .instance(TokenService.class, sessionTokens)
                .singleton(AdminPageDataFactory.class, r -> new AdminPageDataFactory(() -> {
                    var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
                    return provider == null ? null : provider.getPublicWebSiteInfo();
                }, () -> {
                    var provider = zrLogConfig.getWebSetup(AdminResourceProvider.class);
                    return provider == null ? "" : provider.getAdminResource().getStaticResourceBuildId();
                }))
                .singleton(BackendServerUrl.class, r -> new BackendServerUrl(
                        zrLogConfig.getServerConfig()::getPort, zrLogConfig.getServerConfig()::getContextPath, () -> {
                            var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
                            var site = provider == null ? null : provider.getPublicWebSiteInfo();
                            return site == null ? "" : site.getHost();
                        }))
                .singleton(FeatureLabService.class, r -> new FeatureLabService(() -> zrLogConfig.getWebSetup(AccessWebSetup.class) != null))
                .singleton(AdminResourceService.class, r -> new AdminResourceService(this::capabilities, r.get(FeatureLabService.class), () -> {
                    var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
                    return provider == null ? null : provider.getPublicWebSiteInfo();
                }, () -> new com.zrlog.model.WebSite().getStringValueByName("admin_static_resource_base_url")))
                .controller(AdminPublicController.class, (r, request, response) -> new AdminPublicController(r.get(AdminResourceService.class), this::staticHtmlEnabled))
                .singleton(AdminStaticSiteProgress.class, r -> new AdminStaticSiteProgress(() -> zrLogConfig.getPluginsByClazz(StaticSitePlugin.class)))
                .singleton(AdminStaticSiteSsePublisher.class, r -> new AdminStaticSiteSsePublisher(
                        r.get(CacheRefreshService.class)::isStaticSiteDisabled, r.get(AdminStaticSiteProgress.class)))
                .singleton(AdminActionDispatcher.class, r -> new AdminActionDispatcher(
                        r.get(CacheRefreshService.class), r.get(AdminStaticSiteSsePublisher.class)))
                .singleton(TemplateService.class, r -> new TemplateService(r.get(CacheService.class),
                        () -> zrLogConfig.getPlugin(PluginCorePlugin.class), () -> zrLogConfig.getPlugin(TemplateDownloadPlugin.class)))
                .controller(TemplateController.class, (r, request, response) -> new TemplateController(r.get(TemplateService.class), r.get(AdminPageDataFactory.class)))
                .singleton(PluginTokenValidator.class, r -> new PluginTokenValidator(() -> zrLogConfig.getPlugin(PluginCorePlugin.class)))
                .interceptor(AdminInternalAiInterceptor.class, r -> new AdminInternalAiInterceptor(r.get(PluginTokenValidator.class), () -> r.get(AdminActionDispatcher.class)))
                .interceptor(AdminCrossOriginInterceptor.class, r -> new AdminCrossOriginInterceptor(
                        new com.zrlog.admin.business.service.PasskeyRequestContext(() -> {
                            var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
                            var site = provider == null ? null : provider.getPublicWebSiteInfo();
                            return site == null ? "" : site.getHost();
                        })))
                .interceptor(AdminInterceptor.class, r -> new AdminInterceptor(() -> r.get(TokenService.class), () -> r.get(AdminActionDispatcher.class), zrLogConfig::isInstalled))
                .interceptor(AdminLoginInterceptor.class, r -> new AdminLoginInterceptor(() -> r.get(TokenService.class)))
                .interceptor(AdminDevFileInterceptor.class, r -> new AdminDevFileInterceptor(() -> r.get(TokenService.class)))
                .interceptor(AdminRefreshCacheInterceptor.class, r -> new AdminRefreshCacheInterceptor(() -> r.get(TokenService.class), r.get(PluginTokenValidator.class), () -> r.get(AdminActionDispatcher.class)))
                .singleton(SystemInfoUtils.class, r -> new SystemInfoUtils(zrLogConfig::isInstalled, zrLogConfig::getDataSource,
                        () -> zrLogConfig.getUpdater() == null ? null : zrLogConfig.getUpdater().execFile(), zrLogConfig::getProgramUptime))
                .controller(AdminSystemController.class, (r, request, response) -> new AdminSystemController(r.get(SystemInfoUtils.class), r.get(AdminPageDataFactory.class)))
                .singleton(AdminStatisticsService.class, r -> new AdminStatisticsService(r.get(CacheService.class), r.get(SystemInfoUtils.class)))
                .singleton(AdminDashboardService.class, r -> new AdminDashboardService(
                        r.get(AdminStatisticsService.class), new AdminArticleService(r.get(CacheService.class), () -> zrLogConfig.getWebSetup(com.zrlog.admin.business.content.ArticleAssistant.class), r.get(AdminPageDataFactory.class)),
                        () -> zrLogConfig.getPlugin(PluginCorePlugin.class)))
                .singleton(WebSiteSettingsService.class, r -> new WebSiteSettingsService(r.get(CacheService.class), r.get(TokenService.class),
                        () -> zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class)))
                .singleton(UpgradeSettingsService.class, r -> new UpgradeSettingsService(r.get(WebSiteSettingsService.class),
                        () -> UpdaterHosts.dockerControl(zrLogConfig.getUpdater())))
                .singleton(MessageCenterService.class, r -> new MessageCenterService(r.get(UpgradeNoticeService.class)))
                .controller(MessageCenterController.class, (r, request, response) -> new MessageCenterController(r.get(MessageCenterService.class)))
                .controller(UpgradeController.class, (r, request, response) -> new UpgradeController(r.get(UpgradeService.class),
                        () -> zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class), () -> UpdaterHosts.dockerControl(zrLogConfig.getUpdater()), r.get(AdminPageDataFactory.class)))
                .controller(PluginUploadController.class, (r, request, response) -> new PluginUploadController(() -> zrLogConfig.getPlugin(PluginCorePlugin.class)))
                .controller(AdminController.class, (r, request, response) -> new AdminController(r.get(AdminDashboardService.class), r.get(AdminPageDataFactory.class)))
                .controller(WebSiteController.class, (r, request, response) -> new WebSiteController(r.get(WebSiteSettingsService.class), r.get(UpgradeSettingsService.class), r.get(FeatureLabService.class), r.get(AdminPageDataFactory.class)));
        List<Class<? extends Interceptor>> interceptors = zrLogConfig.getServerConfig().getInterceptors();
        interceptors.add(AdminCrossOriginInterceptor.class);
        interceptors.add(AdminLoginInterceptor.class);
        interceptors.add(AdminDevFileInterceptor.class);
        interceptors.add(AdminRefreshCacheInterceptor.class);
        interceptors.add(AdminInternalAiInterceptor.class);
        interceptors.add(AdminInterceptor.class);
        if (EnvKit.isDevMode()) {
            DevKit.configDev(zrLogConfig.getServerConfig());
        }
        //router
        AdminRouters.configAdminRoute(zrLogConfig.getServerConfig().getRouter());
    }

    @Override
    public TokenService getTokenService() {
        return sessionTokens;
    }

    @Override
    public void onDatabaseReady(DataSourceWrapper dataSource) {
        CacheService cache = zrLogConfig.getServerConfig().getBindings().build().get(CacheService.class);
        sessionTokens.updateSessionTimeout(cache.getPublicWebSiteInfo().getSession_timeout());
    }

    @Override
    public Plugins getPlugins() {
        Plugins iPlugins = new Plugins();
        var services = zrLogConfig.getServerConfig().getBindings().build();
        iPlugins.add(new UpdateVersionInfoPlugin(services.get(UpgradeNoticeService.class),
                () -> services.get(CacheService.class).getPublicWebSiteInfo().getLanguage(), zrLogConfig.getUpdater()));
        return iPlugins;
    }
}
