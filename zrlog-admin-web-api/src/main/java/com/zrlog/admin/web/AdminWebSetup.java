package com.zrlog.admin.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminDashboardService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminResourceProvider;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.business.service.AdminStatisticsService;
import com.zrlog.admin.business.service.FeatureLabService;
import com.zrlog.admin.business.service.MessageCenterService;
import com.zrlog.admin.business.service.PasskeyRequestContext;
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
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.admin.web.di.DaggerAdminComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.account.DaggerAccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.di.content.DaggerContentComponent;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.blog.web.plugin.TemplateDownloadPlugin;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.MarkdownRenderer;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DataComponent;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.TokenServiceProvider;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.List;
import java.util.ServiceLoader;

public class AdminWebSetup implements WebSetup, TokenServiceProvider {

    private final ZrLogConfig zrLogConfig;

    public AdminWebSetup(ZrLogConfig zrLogConfig) {
        this.zrLogConfig = zrLogConfig;
        if (zrLogConfig.getServerConfig().isNativeImageAgent()) {
            //register
            AdminNativeImageUtils.reg();
        }
    }

    private PublicWebSiteInfo siteInfo() {
        var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
        return provider == null ? null : provider.getPublicWebSiteInfo();
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
        zrLogConfig
                .getServerConfig()
                .getBindings()
                .singleton(
                        AccountComponent.class,
                        r ->
                                DaggerAccountComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                zrLogConfig::isInstalled,
                                                () -> zrLogConfig.getWebSetup(AvatarStorage.class),
                                                () -> {
                                                    var provider =
                                                            zrLogConfig.getWebSetup(
                                                                    AdminResourceProvider.class);
                                                    return provider == null
                                                            ? java.util.Collections.emptySet()
                                                            : provider.getAdminResource()
                                                                    .getAdminCacheableApiUris();
                                                },
                                                this::siteInfo,
                                                () -> {
                                                    var provider =
                                                            zrLogConfig.getWebSetup(
                                                                    AdminResourceProvider.class);
                                                    return provider == null
                                                            ? ""
                                                            : provider.getAdminResource()
                                                                    .getStaticResourceBuildId();
                                                },
                                                zrLogConfig.getServerConfig()::getPort,
                                                zrLogConfig.getServerConfig()::getContextPath))
                .singleton(TokenService.class, r -> r.get(AccountComponent.class).tokens())
                .singleton(AdminPageDataFactory.class, r -> r.get(AccountComponent.class).pages())
                .singleton(BackendServerUrl.class, r -> r.get(AccountComponent.class).backend())
                .singleton(
                        AdminStaticSiteProgress.class,
                        r ->
                                new AdminStaticSiteProgress(
                                        () ->
                                                zrLogConfig.getPluginsByClazz(
                                                        StaticSitePlugin.class)))
                .singleton(
                        AdminStaticSiteSsePublisher.class,
                        r ->
                                new AdminStaticSiteSsePublisher(
                                        r.get(CacheRefreshService.class)::isStaticSiteDisabled,
                                        r.get(AdminStaticSiteProgress.class)))
                .singleton(
                        AdminActionDispatcher.class,
                        r ->
                                new AdminActionDispatcher(
                                        r.get(CacheRefreshService.class),
                                        r.get(AdminStaticSiteSsePublisher.class),
                                        r.get(DataComponent.class).locks()))
                .singleton(
                        MarkdownRenderer.class,
                        r ->
                                ServiceLoader.load(MarkdownRenderer.class)
                                        .findFirst()
                                        .orElse(markdown -> null))
                .singleton(
                        ContentComponent.class,
                        r ->
                                DaggerContentComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                () ->
                                                        zrLogConfig.getWebSetup(
                                                                ArticleAssistant.class),
                                                () ->
                                                        zrLogConfig.getWebSetup(
                                                                AttachmentStorage.class),
                                                r.get(AdminStaticSiteSsePublisher.class),
                                                r.get(MarkdownRenderer.class)))
                .singleton(
                        SystemInfoUtils.class,
                        r ->
                                new SystemInfoUtils(
                                        zrLogConfig::isInstalled,
                                        zrLogConfig::getDataSource,
                                        () ->
                                                zrLogConfig.getUpdater() == null
                                                        ? null
                                                        : zrLogConfig.getUpdater().execFile(),
                                        zrLogConfig::getProgramUptime))
                .singleton(
                        AdminComponent.class,
                        r ->
                                DaggerAdminComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                () -> r.get(ContentComponent.class),
                                                r.get(TokenService.class),
                                                () -> zrLogConfig.getPlugin(PluginCorePlugin.class),
                                                () ->
                                                        zrLogConfig.getPlugin(
                                                                TemplateDownloadPlugin.class),
                                                () ->
                                                        zrLogConfig.getPlugin(
                                                                UpdateVersionInfoPlugin.class),
                                                () ->
                                                        UpdaterHosts.dockerControl(
                                                                zrLogConfig.getUpdater()),
                                                r.get(SystemInfoUtils.class),
                                                this::staticHtmlEnabled,
                                                () ->
                                                        zrLogConfig.getWebSetup(
                                                                        AccessWebSetup.class)
                                                                != null,
                                                this::capabilities,
                                                this::siteInfo))
                .singleton(
                        AdminDashboardService.class, r -> r.get(AdminComponent.class).dashboard())
                .singleton(
                        AdminStatisticsService.class, r -> r.get(AdminComponent.class).statistics())
                .singleton(FeatureLabService.class, r -> r.get(AdminComponent.class).features())
                .singleton(AdminResourceService.class, r -> r.get(AdminComponent.class).resources())
                .singleton(TemplateService.class, r -> r.get(AdminComponent.class).templates())
                .singleton(
                        WebSiteSettingsService.class, r -> r.get(AdminComponent.class).settings())
                .singleton(
                        UpgradeSettingsService.class,
                        r -> r.get(AdminComponent.class).upgradeSettings())
                .singleton(MessageCenterService.class, r -> r.get(AdminComponent.class).messages())
                .controller(
                        AdminDevController.class,
                        (r, request, response) -> r.get(AdminComponent.class).adminDevController())
                .controller(
                        AdminProxyController.class,
                        (r, request, response) ->
                                r.get(AdminComponent.class).adminProxyController())
                .controller(
                        AdminPublicController.class,
                        (r, request, response) ->
                                r.get(AdminComponent.class).adminPublicController())
                .controller(
                        AdminSystemController.class,
                        (r, request, response) ->
                                r.get(AdminComponent.class).adminSystemController())
                .controller(
                        TemplateController.class,
                        (r, request, response) -> r.get(AdminComponent.class).templateController())
                .controller(
                        MessageCenterController.class,
                        (r, request, response) ->
                                r.get(AdminComponent.class).messageCenterController())
                .controller(
                        UpgradeController.class,
                        (r, request, response) -> r.get(AdminComponent.class).upgradeController())
                .controller(
                        PluginUploadController.class,
                        (r, request, response) ->
                                r.get(AdminComponent.class).pluginUploadController())
                .controller(
                        AdminController.class,
                        (r, request, response) -> r.get(AdminComponent.class).adminController())
                .controller(
                        WebSiteController.class,
                        (r, request, response) -> r.get(AdminComponent.class).webSiteController())
                .singleton(
                        PluginTokenValidator.class,
                        r ->
                                new PluginTokenValidator(
                                        () -> zrLogConfig.getPlugin(PluginCorePlugin.class)))
                .interceptor(
                        AdminInternalAiInterceptor.class,
                        r ->
                                new AdminInternalAiInterceptor(
                                        r.get(PluginTokenValidator.class),
                                        () -> r.get(AdminActionDispatcher.class)))
                .interceptor(
                        AdminCrossOriginInterceptor.class,
                        r ->
                                new AdminCrossOriginInterceptor(
                                        new PasskeyRequestContext(
                                                () ->
                                                        siteInfo() == null
                                                                ? ""
                                                                : siteInfo().getHost())))
                .interceptor(
                        AdminInterceptor.class,
                        r ->
                                new AdminInterceptor(
                                        () -> r.get(TokenService.class),
                                        () -> r.get(AdminActionDispatcher.class),
                                        zrLogConfig::isInstalled,
                                        r.get(AccountComponent.class).preferences(),
                                        r.get(AccountComponent.class).permissions()))
                .interceptor(
                        AdminLoginInterceptor.class,
                        r -> new AdminLoginInterceptor(() -> r.get(TokenService.class)))
                .interceptor(
                        AdminDevFileInterceptor.class,
                        r ->
                                new AdminDevFileInterceptor(
                                        () -> r.get(TokenService.class),
                                        r.get(AccountComponent.class).permissions()))
                .interceptor(
                        AdminRefreshCacheInterceptor.class,
                        r ->
                                new AdminRefreshCacheInterceptor(
                                        () -> r.get(TokenService.class),
                                        r.get(PluginTokenValidator.class),
                                        () -> r.get(AdminActionDispatcher.class),
                                        r.get(AccountComponent.class).permissions()));
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
        return zrLogConfig.getServerConfig().getBindings().build().get(TokenService.class);
    }

    @Override
    public void onDatabaseReady(DataSourceWrapper dataSource) {
        CacheService cache = zrLogConfig.getServerConfig().getBindings().build().get(CacheService.class);
        getTokenService().updateSessionTimeout(cache.getPublicWebSiteInfo().getSession_timeout());
    }

    @Override
    public Plugins getPlugins() {
        Plugins iPlugins = new Plugins();
        var services = zrLogConfig.getServerConfig().getBindings().build();
        iPlugins.add(
                new UpdateVersionInfoPlugin(
                        services.get(UpgradeNoticeService.class),
                        () -> services.get(CacheService.class).getPublicWebSiteInfo().getLanguage(),
                        zrLogConfig.getUpdater(),
                        services.get(ServiceComponent.class).websiteKv()));
        return iPlugins;
    }
}
