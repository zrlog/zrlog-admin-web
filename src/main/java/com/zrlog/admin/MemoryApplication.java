package com.zrlog.admin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.util.IOUtil;
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
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

public class MemoryApplication {

    private static final Logger LOGGER = LoggerUtil.getLogger(MemoryApplication.class);
    private static final int DEFAULT_PORT = 17080;
    private static final Gson GSON = new Gson();
    private static final String CONTEXT_PATH = "/sub";
    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "123456";
    private static final String MEMORY_RUNTIME_DIR = ".zrlog-memory";
    private static final String MEMORY_INSTALL_CONFIG_TEMPLATE_FILE = "conf/memory-install.json";
    private static final String MEMORY_INSTALL_CONFIG_FILE = "conf/memory-install.generated.json";

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
        MemoryRuntime runtime = prepareRuntime(port);
        Constants.zrLogConfig = prepareConfig(runtime, port);
        WebServerBuilder build = new WebServerBuilder.Builder().config(Constants.zrLogConfig).build();
        LOGGER.info("Start ZrLog admin memory application at http://127.0.0.1:" + port + CONTEXT_PATH
                + "/admin/login, root=" + runtime.getRootPath()
                + ", user=" + DEFAULT_USER + ", password=" + DEFAULT_PASSWORD);
        build.start();
    }

    static MemoryRuntime prepareRuntime() throws Exception {
        return prepareRuntime(DEFAULT_PORT);
    }

    static MemoryRuntime prepareRuntime(int port) throws Exception {
        Path rootPath = memoryRootPath();
        resetMemoryRoot(rootPath);
        Files.createDirectories(rootPath.resolve("conf/plugins/installed-plugins"));
        Files.createDirectories(rootPath.resolve("static"));
        Files.createDirectories(rootPath.resolve("logs"));
        PathUtil.setRootPath(rootPath.toString());

        Properties databaseProperties = InMemoryDatabase.h2Properties("zrlog_admin_memory_" + UUID.randomUUID());
        Path installConfigPath = writeInstallConfig(rootPath, databaseProperties, port);
        InstallConstants.installConfig = new MemoryInstallConfig();
        installFromConfigFile(installConfigPath);
        return new MemoryRuntime(rootPath, databaseProperties, installConfigPath);
    }

    static DevZrLogConfig prepareConfig(MemoryRuntime runtime, int port) {
        PathUtil.setRootPath(runtime.getRootPath().toString());
        return new MemoryZrLogConfig(port, null, CONTEXT_PATH);
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

    private static Path writeInstallConfig(Path rootPath, Properties databaseProperties, int port) throws Exception {
        JsonObject installConfig = readInstallConfigTemplate();
        JsonObject dbConfig = installConfig.getAsJsonObject("dbConfig");
        dbConfig.addProperty("driverClass", databaseProperties.getProperty("driverClass"));
        dbConfig.addProperty("jdbcUrl", databaseProperties.getProperty("jdbcUrl"));
        dbConfig.addProperty("user", databaseProperties.getProperty("user"));
        dbConfig.addProperty("password", databaseProperties.getProperty("password"));
        installConfig.getAsJsonObject("appendWebsite").addProperty("host", "localhost:" + port);

        Path installConfigPath = rootPath.resolve(MEMORY_INSTALL_CONFIG_FILE);
        Files.writeString(installConfigPath, GSON.toJson(installConfig), StandardCharsets.UTF_8);
        return installConfigPath;
    }

    private static JsonObject readInstallConfigTemplate() throws Exception {
        Path configPath = Path.of(System.getProperty("user.dir"), MEMORY_INSTALL_CONFIG_TEMPLATE_FILE);
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("Missing " + configPath);
        }
        return GSON.fromJson(Files.readString(configPath), JsonObject.class);
    }

    private static void installFromConfigFile(Path installConfigPath) throws Exception {
        String jsonStr = IOUtil.getStringInputStream(new FileInputStream(installConfigPath.toFile()));
        InstallConfigVO config = GSON.fromJson(jsonStr, InstallConfigVO.class);
        InstallService installService = new InstallService(InstallConstants.installConfig, config);
        boolean install = installService.install();
        if (!install) {
            throw new IllegalStateException("Install memory database failed: " + installConfigPath);
        }
    }

    static final class MemoryRuntime {
        private final Path rootPath;
        private final Properties databaseProperties;
        private final Path installConfigPath;

        private MemoryRuntime(Path rootPath, Properties databaseProperties, Path installConfigPath) {
            this.rootPath = rootPath;
            this.databaseProperties = databaseProperties;
            this.installConfigPath = installConfigPath;
        }

        Path getRootPath() {
            return rootPath;
        }

        Properties getDatabaseProperties() {
            return databaseProperties;
        }

        Path getInstallConfigPath() {
            return installConfigPath;
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
