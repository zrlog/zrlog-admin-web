package com.zrlog.admin.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.automation.ReminderTaskProvider;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.admin.web.di.automation.DaggerAutomationComponent;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.*;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.controller.api.*;
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
                .singleton(AutomationComponent.class, r -> DaggerAutomationComponent.factory().create(
                        r.get(DataComponent.class), r.get(AccountComponent.class), r.get(ServiceComponent.class),
                        () -> {
                            java.util.List<com.zrlog.admin.business.automation.ScheduledTaskProvider> providers = new java.util.ArrayList<>();
                            providers.add(new ReminderTaskProvider());
                            providers.add(new com.zrlog.admin.business.automation.PluginScheduledTaskProvider(
                                    () -> zrLogConfig.getPlugin(com.zrlog.business.plugin.PluginCorePlugin.class)));
                            providers.add(new HostMaintenanceTaskProvider(r.get(CacheService.class), r.get(ServiceComponent.class),
                                    () -> zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class)));
                            providers.add(new com.zrlog.admin.business.automation.AutomaticUpgradeTaskProvider(
                                    r.get(AutomationComponent.class).store(),
                                    new HostAutomaticUpgradeOperations(r.get(ServiceComponent.class).upgrades(),
                                            r.get(ServiceComponent.class).websiteKv(),
                                            () -> zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class), zrLogConfig::getUpdater,
                                            EnvKit::isFaaSMode, new AdminAuditService(r.get(ServiceComponent.class).websiteKv()),
                                            new HostUpgradeResourceSync(r.get(CacheRefreshService.class),
                                                    () -> zrLogConfig.getPluginsByClazz(StaticSitePlugin.class),
                                                    () -> zrLogConfig.getWebSetup(AdminResourceProvider.class) != null,
                                                    () -> HostUpgradeResourceSync.request(siteInfo().getHost(),
                                                            r.get(ServiceComponent.class).websiteKv().getString("admin_static_resource_base_url"),
                                                            zrLogConfig.getRequestConfig(),
                                                            new com.hibegin.http.server.ApplicationContext(zrLogConfig.getServerConfig())),
                                                    new AdminAuditService(r.get(ServiceComponent.class).websiteKv()))),
                                    System::currentTimeMillis));
                            if (zrLogConfig.getWebSetup(AttachmentStorage.class) != null) {
                                providers.add(r.get(com.zrlog.admin.web.di.assets.AssetsComponent.class).cleanupTasks());
                            }
                            if (zrLogConfig.getWebSetup(ContentWebSetup.class) != null) {
                                providers.add(new ScheduledArticleTaskProvider(r.get(ContentComponent.class).contentTools(),
                                        r.get(AccountComponent.class).permissions(), execution -> {
                                            var internalRequest = com.hibegin.http.server.util.HttpRequestBuilder.buildRequest(
                                                    com.hibegin.http.HttpMethod.GET, "/", siteInfo().getHost(), "ZrLog Assistant",
                                                    zrLogConfig.getRequestConfig(), new com.hibegin.http.server.ApplicationContext(zrLogConfig.getServerConfig()));
                                            r.get(CacheRefreshService.class).updateCacheWithinBudget(internalRequest,
                                                    List.of(com.zrlog.business.plugin.type.StaticSiteType.BLOG), execution.runId(),
                                                    execution.actor().getUserId(), execution::remainingMillis,
                                                    () -> {
                                                        try { execution.checkActive(); }
                                                        catch (Exception error) { throw new IllegalStateException("automation.executionExpired", error); }
                                                    });
                                        }));
                            }
                            if (zrLogConfig.getWebSetup(com.zrlog.admin.web.AIWebSetup.class) != null) {
                                providers.add(r.get(com.zrlog.admin.web.di.ai.AIComponent.class).scheduledTasks());
                            }
                            return providers;
                        },
                        EnvKit::isFaaSMode,
                        () -> zrLogConfig.getWebSetup(com.zrlog.admin.web.AIWebSetup.class) == null ? null
                                : r.get(com.zrlog.admin.web.di.ai.AIComponent.class).automationAssistant()))
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
                                        .create(() -> r.get(AutomationComponent.class).publicationScheduler(),
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
                .controller(AutomationController.class,
                        (r, request, response) -> r.get(AutomationComponent.class).controller())
                .interceptor(AutomationTickInterceptor.class,
                        r -> new AutomationTickInterceptor(r.get(AutomationComponent.class).settings(), r.get(AutomationComponent.class).ticks()))
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
                                        r.get(AccountComponent.class).routeAuthorizer(),
                                        r.get(AccountComponent.class).requestIdentity()))
                .interceptor(
                        AdminLoginInterceptor.class,
                        r -> new AdminLoginInterceptor(() -> r.get(TokenService.class)))
                .interceptor(
                        AdminDevFileInterceptor.class,
                        r ->
                                new AdminDevFileInterceptor(
                                        () -> r.get(TokenService.class),
                                        r.get(AccountComponent.class).requestIdentity()))
                .interceptor(
                        AdminRefreshCacheInterceptor.class,
                        r ->
                                new AdminRefreshCacheInterceptor(
                                        () -> r.get(TokenService.class),
                                        r.get(PluginTokenValidator.class),
                                        () -> r.get(AdminActionDispatcher.class),
                                        r.get(AccountComponent.class).requestIdentity()));
        List<Class<? extends Interceptor>> interceptors = zrLogConfig.getServerConfig().getInterceptors();
        interceptors.add(AdminCrossOriginInterceptor.class);
        interceptors.add(AdminLoginInterceptor.class);
        interceptors.add(AdminDevFileInterceptor.class);
        interceptors.add(AdminRefreshCacheInterceptor.class);
        interceptors.add(AdminInternalAiInterceptor.class);
        interceptors.add(AutomationTickInterceptor.class);
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
        zrLogConfig.getServerConfig().getBindings().build().get(ServiceComponent.class).cacheManager().setExternallyScheduled(true);
        try {
            var automations = zrLogConfig.getServerConfig().getBindings().build().get(AutomationComponent.class);
            automations.defaults().initialize();
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("Unable to migrate assistant maintenance tasks", error);
        }
    }

    @Override
    public Plugins getPlugins() {
        Plugins iPlugins = new Plugins();
        var services = zrLogConfig.getServerConfig().getBindings().build();
        var core = zrLogConfig.getPlugin(com.zrlog.business.plugin.PluginCorePlugin.class);
        if (zrLogConfig.isInstalled() && core != null && core.supportsHostScheduler()) {
            try {
                // Called during plugin assembly, before any process starts. Keep legacy scans if migration cannot finish.
                core.setExternallyScheduled(services.get(AutomationComponent.class).defaults().initializePluginTrigger());
            } catch (java.sql.SQLException error) {
                throw new IllegalStateException("Unable to migrate plugin schedule trigger", error);
            }
        }
        iPlugins.add(services.get(AutomationComponent.class).plugin());
        var updates = new UpdateVersionInfoPlugin(
                        services.get(UpgradeNoticeService.class),
                        () -> services.get(CacheService.class).getPublicWebSiteInfo().getLanguage(),
                        zrLogConfig.getUpdater(),
                        services.get(ServiceComponent.class).websiteKv());
        updates.setExternallyScheduled(true);
        iPlugins.add(updates);
        return iPlugins;
    }
}
