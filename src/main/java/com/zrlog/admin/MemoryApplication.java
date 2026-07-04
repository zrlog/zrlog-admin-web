package com.zrlog.admin;

import com.google.gson.Gson;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.WebServerBuilder;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.CacheManagerPlugin;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.install.business.service.InstallService;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.business.vo.InstallSiteConfig;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class MemoryApplication {

    private static final Logger LOGGER = LoggerUtil.getLogger(MemoryApplication.class);
    private static final int DEFAULT_PORT = 17080;
    private static final Gson GSON = new Gson();
    private static final String MEMORY_RUNTIME_DIR = ".zrlog-memory";
    private static final String MEMORY_INSTALL_CONFIG_TEMPLATE_FILE = "conf/memory-install.json";

    static {
        Constants.init();
    }

    public static void main(String[] args) {
        try {
            start(args);
        } catch (Exception e) {
            throw new IllegalStateException("Start memory application failed", e);
        }
    }

    static void start(String[] args) throws Exception {
        System.getProperties().put("sws.run.mode", "dev");
        int port = resolvePort(args);
        Constants.zrLogConfig = prepareConfig(port);
        prepareRuntime(port);
        WebServerBuilder build = new WebServerBuilder.Builder().config(Constants.zrLogConfig).build();
        LOGGER.info("Start ZrLog admin memory application at http://127.0.0.1:" + port + Constants.zrLogConfig.getServerConfig().getContextPath()
                + "/admin/login, root=" + PathUtil.getRootPath());
        build.start();
    }

    static void prepareRuntime(int port) throws Exception {
        Path rootPath = memoryRootPath();
        resetMemoryRoot(rootPath);
        PathUtil.setRootPath(rootPath.toString());

        InstallConfigVO installConfig = readInstallConfigTemplate();
        applyRuntimeConfig(installConfig, port);
        InstallConstants.installConfig = new MemoryInstallConfig();
        installFromConfig(installConfig);
    }

    static DevZrLogConfig prepareConfig(int port) {
        return new MemoryZrLogConfig(port, null, Constants.zrLogConfig.getServerConfig().getContextPath());
    }

    static int resolvePort(String[] args) {
        if (args == null) {
            return DEFAULT_PORT;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        return DEFAULT_PORT;
    }

    private static Path memoryRootPath() {
        return Path.of(System.getProperty("user.dir"), MEMORY_RUNTIME_DIR).toAbsolutePath().normalize();
    }

    private static void resetMemoryRoot(Path rootPath) throws Exception {
        if (!MEMORY_RUNTIME_DIR.equals(rootPath.getFileName().toString())) {
            throw new IllegalStateException("Refuse to reset unexpected memory root: " + rootPath);
        }
        if (!Files.exists(rootPath)) {
            return;
        }
        try (var stream = Files.walk(rootPath)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private static void applyRuntimeConfig(InstallConfigVO installConfig, int port) {
        Map<String, String> appendWebsite = installConfig.getAppendWebsite();
        if (appendWebsite == null) {
            appendWebsite = new LinkedHashMap<>();
            installConfig.setAppendWebsite(appendWebsite);
        }
        appendWebsite.put("host", "localhost:" + port);
    }

    private static InstallConfigVO readInstallConfigTemplate() throws Exception {
        Path configPath = Path.of(System.getProperty("user.dir"), MEMORY_INSTALL_CONFIG_TEMPLATE_FILE);
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("Missing " + configPath);
        }
        InstallConfigVO installConfigVO = GSON.fromJson(Files.readString(configPath), InstallConfigVO.class);
        Properties properties = InMemoryDatabase.h2Properties(installConfigVO.getDbConfig().getDbName());
        installConfigVO.getDbConfig().setJdbcUrl(properties.getProperty("jdbcUrl"));
        installConfigVO.getDbConfig().setDriverClass(properties.getProperty("driverClass"));
        return installConfigVO;
    }

    private static InstallSiteConfig getMemorySiteConfig(InstallConfigVO installConfig) {
        InstallSiteConfig configMsg = installConfig.getConfigMsg();
        if (configMsg == null) {
            throw new IllegalStateException("Missing memory install config value: configMsg");
        }
        requireConfigString(configMsg.getUsername(), "configMsg.username");
        requireConfigString(configMsg.getPassword(), "configMsg.password");
        return configMsg;
    }

    private static String requireConfigString(String value, String path) {
        if (value == null) {
            throw new IllegalStateException("Missing memory install config value: " + path);
        }
        if (value.isEmpty()) {
            throw new IllegalStateException("Empty memory install config value: " + path);
        }
        return value;
    }

    private static void installFromConfig(InstallConfigVO config) throws Exception {
        InstallService installService = new InstallService(InstallConstants.installConfig, config);
        boolean install = installService.install();
        if (!install) {
            throw new IllegalStateException("Install memory database failed");
        }
    }

    private static class MemoryZrLogConfig extends DevZrLogConfig {

        private MemoryZrLogConfig(Integer port, Updater updater, String contextPath) {
            super(port, updater, contextPath);
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            Plugins basePlugins = new Plugins();
            basePlugins.add(new MemoryPluginCorePlugin());
            basePlugins.add(new CacheManagerPlugin(this));
            return basePlugins;
        }
    }

    private static class MemoryInstallConfig extends DefaultInstallConfig {

        @Override
        public String defaultTemplatePath() {
            return Constants.DEFAULT_TEMPLATE_PATH;
        }

        @Override
        public String getZrLogSqlVersion() {
            return String.valueOf(UpgradeVersionHandler.SQL_VERSION);
        }

        @Override
        public File getDbPropertiesFile() {
            return PathUtil.getConfFile("db.properties");
        }
    }

    private static class MemoryPluginCorePlugin implements PluginCorePlugin {

        @Override
        public boolean refreshCache(String cacheVersion, HttpRequest request) {
            return false;
        }

        @Override
        public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request, AdminTokenVO adminTokenVO) {
            return new CloseResponseHandle();
        }

        @Override
        public <T> T requestService(HttpRequest inputRequest, Map<String, String[]> params, AdminTokenVO adminTokenVO,
                                    Class<T> clazz) {
            return null;
        }

        @Override
        public boolean accessPlugin(String uri, HttpRequest request, HttpResponse response, AdminTokenVO adminTokenVO)
                throws URISyntaxException {
            return false;
        }

        @Override
        public String getToken() {
            return "memory";
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean autoStart() {
            return false;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }
}
