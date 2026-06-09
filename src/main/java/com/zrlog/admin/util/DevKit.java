package com.zrlog.admin.util;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;

public class DevKit {


    public static void configDev(ServerConfig serverConfig) {
        serverConfig.addLocalFileStaticResourceMapper(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH, PathUtil.getRootPath(), false);
        serverConfig.addLocalFileStaticResourceMapper(AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH, "/tmp", false);
    }

    public static void disableDev(ServerConfig serverConfig) {
        serverConfig.getStaticResourceMapper().remove(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH + "/");
        serverConfig.getStaticResourceMapper().remove(AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH + "/");
    }
}
