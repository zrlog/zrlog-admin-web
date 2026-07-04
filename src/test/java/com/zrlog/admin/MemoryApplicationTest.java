package com.zrlog.admin;

import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import com.zrlog.install.web.InstallConstants;
import com.zrlog.install.web.config.DefaultInstallConfig;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MemoryApplicationTest {

    @Test
    public void shouldPrepareIsolatedRuntimeWithSeededAdminData() throws Exception {
        String previousRootPath = PathUtil.getRootPath();
        var previousInstallConfig = InstallConstants.installConfig;
        MemoryApplication.MemoryRuntime runtime = null;
        try {
            runtime = MemoryApplication.prepareRuntime();
            assertEquals(Path.of(System.getProperty("user.dir"), ".zrlog-memory").toAbsolutePath().normalize(),
                    runtime.getRootPath());
            assertTrue(Files.exists(runtime.getRootPath().resolve("conf/install.lock")));
            assertTrue(Files.exists(runtime.getRootPath().resolve("conf/db.properties")));
            assertTrue(Files.exists(runtime.getInstallConfigPath()));
            assertTrue(Files.readString(runtime.getInstallConfigPath()).contains("\"dbConfig\""));
            assertTrue(runtime.getDatabaseProperties().getProperty("jdbcUrl").startsWith("jdbc:h2:mem:zrlog_admin_memory_"));

            try (InMemoryDatabase database = InMemoryDatabase.open(runtime.getDatabaseProperties(), false)) {
                assertEquals("ZrLog Memory",
                        database.scalar("select value from website where name=?", "title"));
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        database.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                String password = (String) database.scalar("select password from user where userName=?", "admin");
                assertTrue(PasswordHashUtils.matches(SecurityUtils.md5("123456"), password));
                assertEquals("2c87c6f9-56e7-4d02-a0a2-7d6a53685a21",
                        database.scalar("select secretKey from user where userName=?", "admin"));
            }
        } finally {
            if (runtime != null) {
                deleteTree(runtime.getRootPath());
            }
            PathUtil.setRootPath(previousRootPath);
            InstallConstants.installConfig = previousInstallConfig == null ? new DefaultInstallConfig() : previousInstallConfig;
        }
    }

    @Test
    public void shouldResolveDefaultAndConfiguredPort() {
        assertEquals(17080, MemoryApplication.resolvePort(null));
        assertEquals(17080, MemoryApplication.resolvePort(new String[]{"--debug"}));
        assertEquals(18080, MemoryApplication.resolvePort(new String[]{"--port=18080"}));
    }

    @Test
    public void shouldUseConfiguredPortInGeneratedInstallConfig() throws Exception {
        String previousRootPath = PathUtil.getRootPath();
        var previousInstallConfig = InstallConstants.installConfig;
        MemoryApplication.MemoryRuntime runtime = null;
        try {
            runtime = MemoryApplication.prepareRuntime(18080);
            try (InMemoryDatabase database = InMemoryDatabase.open(runtime.getDatabaseProperties(), false)) {
                assertEquals("localhost:18080",
                        database.scalar("select value from website where name=?", "host"));
            }
        } finally {
            if (runtime != null) {
                deleteTree(runtime.getRootPath());
            }
            PathUtil.setRootPath(previousRootPath);
            InstallConstants.installConfig = previousInstallConfig == null ? new DefaultInstallConfig() : previousInstallConfig;
        }
    }

    @Test
    public void shouldBuildRuntimeConfigFromGeneratedDbProperties() throws Exception {
        String previousRootPath = PathUtil.getRootPath();
        var previousInstallConfig = InstallConstants.installConfig;
        MemoryApplication.MemoryRuntime runtime = null;
        DevZrLogConfig config = null;
        try {
            runtime = MemoryApplication.prepareRuntime(18083);
            config = MemoryApplication.prepareConfig(runtime, 18083);

            assertNotNull(config.getDataSource());
            assertEquals("localhost:18083",
                    scalar(config, "select value from website where name=?", "host"));
        } finally {
            if (config != null) {
                config.stop();
            }
            if (runtime != null) {
                deleteTree(runtime.getRootPath());
            }
            PathUtil.setRootPath(previousRootPath);
            InstallConstants.installConfig = previousInstallConfig == null ? new DefaultInstallConfig() : previousInstallConfig;
        }
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
}
