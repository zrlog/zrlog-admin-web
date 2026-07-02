package com.zrlog.admin.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.sun.management.OperatingSystemMXBean;
import com.zrlog.admin.web.controller.api.AdminController;
import com.zrlog.common.Constants;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

public class ServerInfoUtils {
    public static List<ServerInfo> convertToServerInfos(ServerInfoSnapshot data) {
        List<ServerInfo> systemInfo = new ArrayList<>();
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.system"), data.getOsName() + " - " + data.getOsArch() + " - " + data.getOsVersion(), "system"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.runPath"), data.getRuntimePath(), "runPath"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.runtime"), data.getJavaVmName() + " - " + data.getJavaVersion(), "runtime"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.webServer"), data.getWebServer(), "webServer"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.timezone"), data.getTimezone(), "timezone"));
        Locale locale = Locale.getDefault();
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.locale"), locale.getLanguage() + "/" + (StringUtils.isNotEmpty(locale.getCountry()) ? locale.getCountry() : "Unknown"), "locale"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.dbInfo"), data.getDbServerVersion(), "dbInfo"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.cpuInfo"), CPUInfo.getInstance().getCpuModel(), "cpuInfo"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.encoding"), data.getFileEncoding(), "encoding"));
        systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.programInfo"), BlogBuildInfoUtil.getVersionInfo(), "programInfo"));
        return systemInfo;
    }

    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return total - free;
    }

    private static List<String> geAllFolders() {
        if (EnvKit.isFaaSMode()) {
            return new ArrayList<>(Arrays.asList("/tmp", ZrLogUtil.getFaaSRoot()));
        }
        ArrayList<String> allFileList = new ArrayList<>(Arrays.asList(
                PathUtil.getTempPath(),
                PathUtil.getLogPath(),
                PathUtil.getConfPath(),
                PathUtil.getStaticPath(),
                PathUtil.getCachePath(),
                PathUtil.getRootPath() + "/doc",
                PathUtil.getRootPath() + "/LICENSE",
                PathUtil.getRootPath() + "/README.en-us.md",
                PathUtil.getRootPath() + "/README.md",
                PathUtil.getRootPath() + "/bin",
                PathUtil.getRootPath() + "/lib"
        ));
        File execFile = getExecFile();
        if (Objects.nonNull(execFile)) {
            allFileList.add(execFile.toString());
        }
        return allFileList;
    }

    public static List<File> getCachedFiles() {
        List<File> cacheFileList = new ArrayList<>();
        FileUtils.getAllFiles(PathUtil.getCachePath(), cacheFileList);
        return cacheFileList;
    }


    public static File getExecFile() {
        if (Objects.isNull(Constants.zrLogConfig.getUpdater())) {
            return null;
        }
        return Constants.zrLogConfig.getUpdater().execFile();
    }

    private static long getFileTotalLength(List<File> files) {
        return files.stream().filter(e -> !Files.isSymbolicLink(Path.of(e.toURI()))).mapToLong(File::length).sum();
    }

    private static List<File> getAllFiles() {
        List<File> allFileList = new ArrayList<>();
        for (String folder : geAllFolders()) {
            FileUtils.getAllFiles(folder, allFileList);
        }
        return allFileList;
    }

    public static List<ServerInfo> getServerInfos2() {

        List<ServerInfo> systemInfo = new ArrayList<>();
        try {

            // 获取堆内存的使用情况
            OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.usedCacheSpace"), formatFileSize(getFileTotalLength(getCachedFiles())), "usedCacheSpace"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.usedDiskSpace"), formatFileSize(getFileTotalLength(getAllFiles())), "usedDiskSpace"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.usedMemorySpace"), formatFileSize(getUsedMemory()), "usedMemorySpace"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.totalMemorySpace"), formatFileSize(osMXBean.getTotalPhysicalMemorySize()), "totalMemorySpace"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.cpuLoad"), CPUInfo.getInstance().getCpuLoad(), "cpuLoad"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.systemLoad"), SystemLoad.getSystemLoad(), "systemLoad"));
            DataSourceWrapper dataSourceWrapper = (DataSourceWrapper) Constants.zrLogConfig.getDataSource();
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.dbConnectSize"),
                    dataSourceWrapper.getDatabaseConnectPoolInfo().getConnectActiveSize() + " / " +
                            dataSourceWrapper.getDatabaseConnectPoolInfo().getConnectTotalSize(), "dbConnectSize"));
            systemInfo.add(new ServerInfo(I18nUtil.getAdminBackendStringFromRes("admin.system.serverInfo.uptime"), Constants.zrLogConfig.getProgramUptime(), "uptime"));
            return systemInfo;
        } catch (Exception e) {
            LoggerUtil.getLogger(AdminController.class).warning("Load server info error " + e.getMessage());
        }
        return systemInfo;
    }

    static String formatFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString;
        if (fileS < 1024) {
            fileSizeString = df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + "K";
        } else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + "M";
        } else if (fileS < 1099511627776L) {
            fileSizeString = df.format((double) fileS / 1073741824) + "G";
        } else {
            fileSizeString = df.format((double) fileS / 1099511627776L) + "T";
        }
        return fileSizeString;
    }

}
