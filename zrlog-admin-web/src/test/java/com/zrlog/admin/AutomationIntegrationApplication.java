package com.zrlog.admin;

import com.google.gson.Gson;
import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePluginImpl;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.install.business.service.InstallService;
import com.zrlog.install.business.service.LocalSqliteSupport;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.data.di.DataComponent;
import com.zrlog.plugin.IPlugin;
import com.zrlog.test.support.MemoryRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

/** Local acceptance entry point: real Admin assembly, SQLite, host process launcher and supplied Core/plugin jars. */
public final class AutomationIntegrationApplication {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("zrlog.integration.project")).toRealPath();
        if (!project.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toRealPath()))
            throw new IllegalArgumentException("Integration project must be in the temporary directory");
        Path core = Path.of(System.getProperty("zrlog.integration.core")).toRealPath();
        Path fixture = Path.of(System.getProperty("zrlog.integration.fixture")).toRealPath();
        System.setProperty("user.dir", project.toString());
        System.setProperty("sws.run.mode", "dev");
        Constants.init();
        int port = MemoryRuntime.resolvePort(args, 18149);
        Path root = MemoryRuntime.reset(project);
        PathUtil.setRootPath(root.toString());
        System.setProperty("sws.root.path", root.toString());
        for (String directory : List.of("conf", "static", "cache", "log", "temp")) {
            Files.createDirectories(root.resolve(directory));
            System.setProperty("sws." + directory + ".path", root.resolve(directory).toString());
        }

        var installer = new DefaultInstallConfig() {
            @Override public String getZrLogSqlVersion() { return String.valueOf(UpgradeVersionHandler.SQL_VERSION); }
            @Override public String defaultTemplatePath() { return Constants.getDefaultTemplatePath(); }
        };
        InstallConfigVO install = new Gson().fromJson(Files.readString(project.resolve("conf/memory-install.json")), InstallConfigVO.class);
        install.setDbConfig(LocalSqliteSupport.createDatabaseConfig(installer));
        if (install.getAppendWebsite() == null) install.setAppendWebsite(new LinkedHashMap<>());
        install.getAppendWebsite().put("host", "localhost:" + port);
        if (!new InstallService(installer, install).install()) throw new IllegalStateException("Integration installation failed");
        Path plugins = Files.createDirectories(root.resolve("conf/plugins/installed-plugins"));
        Files.copy(core, plugins.getParent().resolve("plugin-core.jar"));
        Files.copy(fixture, plugins.resolve(fixture.getFileName()));

        var config = new DevZrLogConfig(port, null, install.getContextPath()) {
            @Override public boolean isTest() { return true; }
            @Override public List<IPlugin> getBasePluginList() {
                var services = getServerConfig().getBindings().build();
                return List.of(new PluginCorePluginImpl(dbPropertiesFile, getServerConfig().getContextPath(),
                                getServerConfig()::getPort, services.get(CacheService.class)::getPublicWebSiteInfo),
                        services.get(ServiceComponent.class).cacheManager());
            }
        };
        // Exercise external ticks first; enable the real resident timer later through the settings API.
        var automations = config.getServerConfig().getBindings().build().get(AutomationComponent.class);
        var actor = AccountAccess.from(config.getServerConfig().getBindings().build().get(DataComponent.class).user().loadById(1));
        automations.settings().save(actor, automations.settings().read(actor).revision, false, false);
        new WebServerBuilder.Builder().config(config).build().start();
    }
}
