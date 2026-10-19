package com.zrlog.admin.web;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminPageService;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.AdminResourceImpl;
import com.zrlog.admin.business.service.AdminResourceProvider;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.util.AdminUiNativeImageUtils;
import com.zrlog.admin.web.config.AdminAccountPages;
import com.zrlog.admin.web.controller.api.AdminStaticSiteController;
import com.zrlog.admin.web.controller.page.AdminManifestController;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.admin.web.controller.page.AdminTemplatePageController;
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ui.AdminUiComponent;
import com.zrlog.admin.web.di.ui.DaggerAdminUiComponent;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.di.DataComponent;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.List;

/** React pages, PWA and static publishing; the API can run without this provider. */
public class AdminUiWebSetup implements WebSetup, AdminResourceProvider {
    private final ZrLogConfig config;
    private final String contextPath;
    private final AdminResource resources;

    public AdminUiWebSetup(ZrLogConfig config, String contextPath) {
        this.config = config;
        this.contextPath = contextPath;
        this.resources = new AdminResourceImpl(contextPath, () -> config.isInstalled()
                ? config.getServerConfig().getBindings().build().get(CacheService.class).getWebSiteVersion() : 0L,
                request -> config.getServerConfig().getBindings().build().get(AdminResourceService.class)
                        .staticResourceBaseUrl(request));
        if (config.getServerConfig().isNativeImageAgent()) AdminUiNativeImageUtils.reg(resources);
    }

    @Override public AdminResource getAdminResource() {
        return resources;
    }

    @Override public void setup() {
        config.getServerConfig()
                .getBindings()
                .singleton(
                        AdminUiComponent.class,
                        r ->
                                DaggerAdminUiComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                r.get(AdminComponent.class),
                                                r.get(TokenService.class),
                                                () -> config.getPlugin(PluginCorePlugin.class),
                                                () ->
                                                        config.getPlugin(
                                                                AdminStaticResourcePlugin.class),
                                                () -> r.get(AdminStaticSiteSsePublisher.class),
                                                resources::getStaticResourceBuildId,
                                                config::isInstalled))
                .singleton(AdminPageService.class, r -> r.get(AdminUiComponent.class).pages())
                .controller(
                        AdminStaticSiteController.class,
                        (r, request, response) -> r.get(AdminUiComponent.class).staticSites())
                .controller(
                        AdminTemplatePageController.class,
                        (r, request, response) -> r.get(AdminUiComponent.class).templates())
                .controller(
                        AdminManifestController.class,
                        (r, request, response) -> r.get(AdminUiComponent.class).manifest())
                .controller(
                        AdminPageController.class,
                        (r, request, response) -> r.get(AdminUiComponent.class).pageController())
                .interceptor(
                        AdminPluginInterceptor.class,
                        r -> r.get(AdminUiComponent.class).pluginInterceptor())
                .interceptor(AdminPwaInterceptor.class, r -> new AdminPwaInterceptor(resources))
                .interceptor(
                        AdminStaticResourceInterceptor.class,
                        r ->
                                new AdminStaticResourceInterceptor(
                                        () -> config.getPlugin(AdminStaticResourcePlugin.class)));
        List<Class<? extends Interceptor>> interceptors = config.getServerConfig().getInterceptors();
        interceptors.addAll(interceptors.indexOf(AdminLoginInterceptor.class), List.of(
                PwaInterceptor.class, AdminPwaInterceptor.class,
                AdminStaticResourceInterceptor.class, AdminPluginInterceptor.class));
        Router router = config.getServerConfig().getRouter();
        router.addMapper(AdminConstants.ADMIN_URI_BASE_PATH, AdminPageController.class);
        resources.getAdminPageUris().forEach(uri -> router.addMapper(
                uri.substring(contextPath.length()), AdminPageController.class, "index"));
        AdminAccountPages.PAGE_APIS.keySet().forEach(uri -> router.addMapper(uri, AdminPageController.class, "index"));
        router.addMapper(AdminConstants.ADMIN_URI_BASE_PATH + "/template/download", AdminTemplatePageController.class, "download");
        router.addMapper(TemplateInfoHelper.ADMIN_PREVIEW_IMAGE_URI, AdminTemplatePageController.class, "previewImage");
        router.addMapper(AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH, AdminManifestController.class, "manifest");
        router.addMapper("/api/admin/static-site", AdminStaticSiteController.class);
        for (String uri : AdminConstants.adminStaticResources) {
            config.getServerConfig().addStaticResourceMapper(uri, uri, AdminUiWebSetup.class::getResourceAsStream);
        }
    }

    @Override public Plugins getPlugins() {
        Plugins plugins = new Plugins();
        ApplicationContext context = new ApplicationContext(config.getServerConfig());
        context.init();
        plugins.add(
                new AdminStaticResourcePlugin(
                        new StaticSiteRenderer(
                                config.getRequestConfig(), config.getResponseConfig(), context),
                        () -> config.getWebSetup(WebSiteInfoProvider.class).getPublicWebSiteInfo(),
                        resources,
                        contextPath,
                        () ->
                                config.getServerConfig()
                                        .getBindings()
                                        .build()
                                        .get(DataComponent.class)
                                        .website()));
        return plugins;
    }
}
