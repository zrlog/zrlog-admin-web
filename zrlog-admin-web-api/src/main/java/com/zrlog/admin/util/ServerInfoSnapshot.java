package com.zrlog.admin.util;

import java.util.Properties;

public class ServerInfoSnapshot {

    private final String osName;
    private final String osArch;
    private final String osVersion;
    private final String runtimePath;
    private final String javaVmName;
    private final String javaVersion;
    private final String webServer;
    private final String timezone;
    private final String dbServerVersion;
    private final String fileEncoding;

    public ServerInfoSnapshot(String osName, String osArch, String osVersion, String runtimePath, String javaVmName,
                              String javaVersion, String webServer, String timezone, String dbServerVersion,
                              String fileEncoding) {
        this.osName = osName;
        this.osArch = osArch;
        this.osVersion = osVersion;
        this.runtimePath = runtimePath;
        this.javaVmName = javaVmName;
        this.javaVersion = javaVersion;
        this.webServer = webServer;
        this.timezone = timezone;
        this.dbServerVersion = dbServerVersion;
        this.fileEncoding = fileEncoding;
    }

    public static ServerInfoSnapshot fromProperties(Properties properties) {
        return new ServerInfoSnapshot(
                value(properties, "os.name"),
                value(properties, "os.arch"),
                value(properties, "os.version"),
                value(properties, "zrlog.runtime.path"),
                value(properties, "java.vm.name"),
                value(properties, "java.version"),
                value(properties, "server.info"),
                value(properties, "user.timezone"),
                value(properties, "dbServer.version"),
                value(properties, "file.encoding")
        );
    }

    public ServerInfoSnapshot withWebServer(String webServer) {
        return new ServerInfoSnapshot(osName, osArch, osVersion, runtimePath, javaVmName, javaVersion, webServer,
                timezone, dbServerVersion, fileEncoding);
    }

    public String getOsName() {
        return osName;
    }

    public String getOsArch() {
        return osArch;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public String getRuntimePath() {
        return runtimePath;
    }

    public String getJavaVmName() {
        return javaVmName;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getWebServer() {
        return webServer;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDbServerVersion() {
        return dbServerVersion;
    }

    public String getFileEncoding() {
        return fileEncoding;
    }

    private static String value(Properties properties, String key) {
        Object value = properties.get(key);
        return value == null ? null : value.toString();
    }
}
