package com.zrlog.admin;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.business.plugin.CacheManagerPlugin;
import com.zrlog.business.plugin.PluginCorePluginImpl;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.Updater;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.updater.spi.UpdateProviders;
import com.zrlog.data.cache.CacheServiceImpl;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupLoader;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static com.zrlog.common.Constants.getZrLogHome;

public class Application {

    static {
        String home = getZrLogHome();
        if (Objects.nonNull(home)) {
            PathUtil.setRootPath(home);
        }
    }

    public static Updater getUpdater(String[] args) {
        try {
            File jarFile = new File(PathUtil.getRootPath() + "/zrlog-starter.jar");
            return UpdaterHosts.adapt(UpdateProviders.create(UpdaterTypeEnum.ZIP, UpdaterHosts.builder(args, jarFile).build()));
        } catch (Throwable e) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.getProperties().put("sws.run.mode", "dev");
        Constants.zrLogConfig = new DevZrLogConfig(17080, getUpdater(args), "/sub");
        WebServerBuilder build = new WebServerBuilder.Builder().config(Constants.zrLogConfig).build();
        build.start();
    }
}

class DevZrLogConfig extends ZrLogConfig {

    protected DevZrLogConfig(Integer port, Updater updater, String contextPath) {
        super(port, updater, contextPath);
        webSetups.addAll(WebSetupLoader.load(
                new WebSetupContext(this, dbPropertiesFile, installLockFile, contextPath, updater),
                provider -> provider.name().equals("admin") || provider.name().startsWith("admin-")));
        this.webSetups.forEach(WebSetup::setup);
        serverConfig.getInterceptors().add(MethodInterceptor.class);
    }

    @Override
    public DataSourceWrapper configDatabase() throws Exception {
        this.dataSource = super.configDatabase();
        if (Objects.nonNull(dataSource)) {
            cacheService = new CacheServiceImpl();
            new DbUpgradeService(this.dataSource, this.cacheService.getCurrentSqlVersion()).tryDoUpgrade();
        }
        return dataSource;
    }

    @Override
    protected TokenService initTokenService() {
        if (Objects.isNull(cacheService)) {
            return null;
        }
        long sessionTimeout = this.cacheService.getPublicWebSiteInfo().getSession_timeout();
        return new AdminTokenService(sessionTimeout);
    }

    @Override
    public List<IPlugin> getBasePluginList() {
        Plugins basePlugins = new Plugins();
        basePlugins.add(new PluginCorePluginImpl(dbPropertiesFile, serverConfig.getContextPath()));
        basePlugins.add(new CacheManagerPlugin(this));
        return basePlugins;
    }
}
