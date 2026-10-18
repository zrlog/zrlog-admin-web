package com.zrlog.admin.web;

import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.AdminResourceImpl;
import com.zrlog.admin.util.AdminUiNativeImageUtils;
import com.zrlog.admin.web.config.AdminAccountPages;
import com.zrlog.admin.web.controller.api.AdminStaticSiteController;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.admin.web.controller.page.AdminTemplatePageController;
import com.zrlog.admin.web.controller.page.AdminManifestController;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import java.util.List;

/** React pages, PWA and static publishing; the API can run without this provider. */
public class AdminUiWebSetup implements WebSetup {
    private final ZrLogConfig config;
    private final String contextPath;
    private final AdminResource resources;

    public AdminUiWebSetup(ZrLogConfig config, String contextPath) {
        this.config = config;
        this.contextPath = contextPath;
        this.resources = new AdminResourceImpl(contextPath);
        AdminConstants.adminResource = resources;
        if (config.getServerConfig().isNativeImageAgent()) AdminUiNativeImageUtils.reg(resources);
    }

    @Override public void setup() {
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
        plugins.add(new AdminStaticResourcePlugin(config, resources, contextPath));
        return plugins;
    }
}
