package com.zrlog.admin.web;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.util.AdminNativeImageUtils;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;

import java.util.List;

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
        List<Class<? extends Interceptor>> interceptors = zrLogConfig.getServerConfig().getInterceptors();
        interceptors.add(AdminCrossOriginInterceptor.class);
        interceptors.add(AdminLoginInterceptor.class);
        interceptors.add(AdminDevFileInterceptor.class);
        interceptors.add(AdminRefreshCacheInterceptor.class);
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
