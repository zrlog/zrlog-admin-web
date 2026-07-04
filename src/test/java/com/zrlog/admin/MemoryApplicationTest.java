package com.zrlog.admin;

import com.google.gson.Gson;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import com.zrlog.install.business.vo.InstallConfigVO;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import com.zrlog.install.web.config.InstallConfig;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Comparator;
import java.util.Properties;

import static org.junit.Assert.*;

public class MemoryApplicationTest {

    private static final Gson GSON = new Gson();
    private static final String MEMORY_INSTALL_CONFIG = "conf/memory-install.json";
    private static final String CUSTOM_DB_NAME = "custom_memory_test";

    @Test
    public void shouldPrepareRuntimeFromMemoryInstallConfig() throws Exception {
        String previousRootPath = PathUtil.getRootPath();
        InstallConfig previousInstallConfig = InstallConstants.installConfig;
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path runtimeRoot = memoryRoot(projectRoot);
        DevZrLogConfig config = null;
        try {
            MemoryApplication.prepareRuntime(18080);

            assertEquals(runtimeRoot.toString(), PathUtil.getRootPath());
            assertTrue(Files.exists(runtimeRoot.resolve("conf/install.lock")));
            assertTrue(Files.exists(runtimeRoot.resolve("conf/db.properties")));
            assertFalse(Files.exists(runtimeRoot.resolve("conf/memory-install.generated.json")));

            InstallConfigVO installConfig = readInstallConfig(projectRoot);
            Properties dbProperties = loadDbProperties(runtimeRoot);
            assertTrue(dbProperties.getProperty("jdbcUrl").contains("mem:" + installConfig.getDbConfig().getDbName()));
            assertEquals(installConfig.getDbConfig().getUser(), dbProperties.getProperty("user"));

            try (InMemoryDatabase database = InMemoryDatabase.open(dbProperties, false)) {
                assertEquals(installConfig.getConfigMsg().getTitle(),
                        database.scalar("select value from website where name=?", "title"));
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        database.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                assertEquals("localhost:18080", database.scalar("select value from website where name=?", "host"));
                String password = (String) database.scalar("select password from user where userName=?",
                        installConfig.getConfigMsg().getUsername());
                assertTrue(PasswordHashUtils.matches(SecurityUtils.md5(installConfig.getConfigMsg().getPassword()), password));
                assertEquals(installConfig.getConfigMsg().getSecretKey(),
                        database.scalar("select secretKey from user where userName=?",
                                installConfig.getConfigMsg().getUsername()));
            }

            config = MemoryApplication.prepareConfig(18080, installConfig.getContextPath());
            assertEquals(installConfig.getContextPath(), config.getServerConfig().getContextPath());
            assertNotNull(config.getDataSource());
            assertEquals("localhost:18080", scalar(config, "select value from website where name=?", "host"));
        } finally {
            if (config != null) {
                config.stop();
            }
            deleteTree(runtimeRoot);
            restoreRuntime(previousRootPath, previousInstallConfig);
        }
    }

    @Test
    public void shouldUseDatabaseNameFromInstallConfigTemplate() throws Exception {
        String previousUserDir = System.getProperty("user.dir");
        String previousRootPath = PathUtil.getRootPath();
        InstallConfig previousInstallConfig = InstallConstants.installConfig;
        Path testProjectRoot = Files.createTempDirectory("zrlog-admin-memory-test-");
        Path runtimeRoot = memoryRoot(testProjectRoot);
        try {
            Files.createDirectories(testProjectRoot.resolve("conf"));
            InstallConfigVO installConfig = readInstallConfig(Path.of(previousUserDir));
            installConfig.getDbConfig().setDbName(CUSTOM_DB_NAME);
            Files.writeString(testProjectRoot.resolve(MEMORY_INSTALL_CONFIG), GSON.toJson(installConfig));

            System.setProperty("user.dir", testProjectRoot.toString());
            MemoryApplication.prepareRuntime(18180);

            Properties dbProperties = loadDbProperties(runtimeRoot);
            String jdbcUrl = dbProperties.getProperty("jdbcUrl");
            assertTrue(jdbcUrl, jdbcUrl.contains("mem:" + CUSTOM_DB_NAME));
            assertFalse(jdbcUrl, jdbcUrl.contains("mem:zrlog_admin_memory"));
            try (InMemoryDatabase database = InMemoryDatabase.open(dbProperties, false)) {
                assertEquals("localhost:18180", database.scalar("select value from website where name=?", "host"));
            }
        } finally {
            System.setProperty("user.dir", previousUserDir);
            deleteTree(testProjectRoot);
            restoreRuntime(previousRootPath, previousInstallConfig);
        }
    }

    @Test
    public void shouldResolveDefaultAndConfiguredPort() {
        assertEquals(17080, MemoryApplication.resolvePort(null));
        assertEquals(17080, MemoryApplication.resolvePort(new String[]{"--debug"}));
        assertEquals(18080, MemoryApplication.resolvePort(new String[]{"--port=18080"}));
    }

    private static InstallConfigVO readInstallConfig(Path projectRoot) throws Exception {
        return GSON.fromJson(Files.readString(projectRoot.resolve(MEMORY_INSTALL_CONFIG)), InstallConfigVO.class);
    }

    private static Properties loadDbProperties(Path runtimeRoot) throws Exception {
        Properties properties = new Properties();
        try (var inputStream = Files.newInputStream(runtimeRoot.resolve("conf/db.properties"))) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static Path memoryRoot(Path projectRoot) {
        return projectRoot.resolve(".zrlog-memory").toAbsolutePath().normalize();
    }

    private static Object scalar(DevZrLogConfig config, String sql, String value) throws Exception {
        try (Connection connection = config.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getObject(1) : null;
            }
        }
    }

    private static void deleteTree(Path rootPath) throws Exception {
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

    private static void restoreRuntime(String previousRootPath, InstallConfig previousInstallConfig) {
        PathUtil.setRootPath(previousRootPath);
        InstallConstants.installConfig = previousInstallConfig == null
                ? new DefaultInstallConfig()
                : previousInstallConfig;
    }
}
