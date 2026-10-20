package com.zrlog.admin.util;

import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.util.ZrLogUtil;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SystemInfoUtils {

    public static final Logger LOGGER = LoggerUtil.getLogger(SystemInfoUtils.class);
    private final BooleanSupplier installed;
    private final Supplier<DataSource> dataSource;
    private final Supplier<File> executable;
    private final Supplier<String> uptime;

    public SystemInfoUtils(BooleanSupplier installed, Supplier<DataSource> dataSource,
                           Supplier<File> executable, Supplier<String> uptime) {
        this.installed = Objects.requireNonNull(installed);
        this.dataSource = Objects.requireNonNull(dataSource);
        this.executable = Objects.requireNonNull(executable);
        this.uptime = Objects.requireNonNull(uptime);
    }

    static String getDatabaseServerVersion(DataSource dataSource) {
        if (Objects.isNull(dataSource)) {
            return "Unknown";
        }
        try {
            if (dataSource instanceof DataSourceWrapperImpl) {
                DataSourceWrapperImpl dataSourceWrapper = (DataSourceWrapperImpl) dataSource;
                if (isSqlite(dataSourceWrapper)) {
                    try (Connection connection = dataSourceWrapper.getConnection()) {
                        String version = connection.getMetaData().getDatabaseProductVersion();
                        return StringUtils.isEmpty(version) ? "Unknown" : version;
                    }
                }
                return dataSourceWrapper.getDbInfo();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DB connect error ", e);
        }
        return "Unknown";
    }

    private static boolean isSqlite(DataSourceWrapperImpl dataSource) {
        String jdbcUrl = dataSource.getJdbcUrl();
        if (StringUtils.isEmpty(jdbcUrl)) {
            jdbcUrl = dataSource.getDataSourceProperties().getProperty("jdbcUrl");
        }
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:");
    }

    private Properties getSystemProp() {
        Properties systemProp = new Properties();
        systemProp.putAll(System.getProperties());
        systemProp.setProperty("zrlog.runtime.path", PathUtil.getRootPath());
        systemProp.setProperty("server.info", "SimpleWebServer/" + com.hibegin.http.server.util.ServerInfo.getVersion());
        if (StringUtils.isNotEmpty(systemProp.getProperty("os.name"))) {
            if (systemProp.get("os.name").toString().startsWith("Mac")) {
                systemProp.put("os.type", "apple");
            } else {
                systemProp.put("os.type", systemProp.getProperty("os.name").toLowerCase());
            }
            systemProp.put("docker", ZrLogUtil.isDockerMode() ? "docker" : "");
        }
        systemProp.remove("dbServer.version");
        if (installed.getAsBoolean()) {
            systemProp.put("dbServer.version", getDatabaseServerVersion(dataSource.get()));
        }
        return systemProp;
    }

    public List<ServerInfo> serverInfo(HttpRequest httpRequest) throws SQLException {
        ServerInfoSnapshot snapshot = ServerInfoSnapshot.fromProperties(getSystemProp());
        String applicationName = httpRequest.getServerConfig().getApplicationName();
        if (applicationName.startsWith("zrlog")) {
            snapshot = snapshot.withWebServer(com.hibegin.http.server.util.ServerInfo.getName()
                    + "/" + com.hibegin.http.server.util.ServerInfo.getVersion());
        } else {
            snapshot = snapshot.withWebServer(applicationName + "/" + httpRequest.getServerConfig().getApplicationVersion());
        }
        return ServerInfoUtils.convertToServerInfos(snapshot);
    }

    public List<ServerInfo> systemIOInfoVO() {
        return ServerInfoUtils.getServerInfos2(dataSource.get(), executable.get(), uptime.get());
    }
}
