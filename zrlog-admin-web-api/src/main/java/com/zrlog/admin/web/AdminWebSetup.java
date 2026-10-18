package com.zrlog.admin.web;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminDashboardService;
import com.zrlog.admin.business.service.AdminStatisticsService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.util.AdminNativeImageUtils;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.controller.api.AdminController;
import com.zrlog.admin.web.controller.api.WebSiteController;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import java.util.List;
import com.zrlog.admin.web.interceptor.*;

public class AdminWebSetup implements WebSetup {

    private final ZrLogConfig zrLogConfig;

    public AdminWebSetup(ZrLogConfig zrLogConfig) {
        this.zrLogConfig = zrLogConfig;
        AdminConstants.adminResource = null;
        if (zrLogConfig.getServerConfig().isNativeImageAgent()) {
            //register
            AdminNativeImageUtils.reg();
        }
    }

    @Override
    public void setup() {
        zrLogConfig.getServerConfig().getBindings()
                .singleton(AdminStatisticsService.class, r -> new AdminStatisticsService(r.get(CacheService.class)))
                .singleton(AdminDashboardService.class, r -> new AdminDashboardService(
                        r.get(AdminStatisticsService.class), new AdminArticleService(r.get(CacheService.class))))
                .singleton(WebSiteSettingsService.class, r -> new WebSiteSettingsService(r.get(CacheService.class)))
                .controller(AdminController.class, (r, request, response) -> new AdminController(r.get(AdminDashboardService.class)))
                .controller(WebSiteController.class, (r, request, response) -> new WebSiteController(r.get(WebSiteSettingsService.class)));
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
    public Plugins getPlugins() {
        Plugins iPlugins = new Plugins();
        iPlugins.add(new UpdateVersionInfoPlugin());
        return iPlugins;
    }
}
