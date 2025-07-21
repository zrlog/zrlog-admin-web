package com.zrlog.admin.web;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.util.AdminNativeImageUtils;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.admin.web.plugin.UpdateVersionInfoPlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.dto.*;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;

import java.util.List;

public class AdminWebSetup implements WebSetup {

    private final ZrLogConfig zrLogConfig;
    private final AdminResource adminResource;
    private final String contextPath;

    public AdminWebSetup(ZrLogConfig zrLogConfig, AdminResource adminResource, String contextPath) {
        this.zrLogConfig = zrLogConfig;
        this.adminResource = adminResource;
        this.contextPath = contextPath;
        AdminConstants.adminResource = adminResource;
        if (zrLogConfig.getServerConfig().isNativeImageAgent()) {
            //register
            AdminNativeImageUtils.reg(adminResource);
        }
    }

    @Override
    public void setup() {
        List<Class<? extends Interceptor>> interceptors = zrLogConfig.getServerConfig().getInterceptors();
        //admin
        interceptors.add(AdminCrossOriginInterceptor.class);
        interceptors.add(PwaInterceptor.class);
        interceptors.add(AdminPwaInterceptor.class);
        interceptors.add(AdminStaticResourceInterceptor.class);
        interceptors.add(AdminPluginInterceptor.class);
        interceptors.add(AdminInterceptor.class);
        if (EnvKit.isDevMode()) {
            DevKit.configDev(zrLogConfig.getServerConfig());
        }
        //router
        AdminRouters.configAdminRoute(zrLogConfig.getServerConfig().getRouter(), adminResource, contextPath);
        for (String uri : AdminConstants.adminStaticResources) {
            zrLogConfig.getServerConfig().addStaticResourceMapper(uri, uri, AdminWebSetup.class::getResourceAsStream);
        }
    }

    @Override
    public Plugins getPlugins() {
        Plugins iPlugins = new Plugins();
        iPlugins.add(new UpdateVersionInfoPlugin());
        iPlugins.add(new AdminStaticResourcePlugin(zrLogConfig, adminResource, contextPath));
        return iPlugins;
    }
}
