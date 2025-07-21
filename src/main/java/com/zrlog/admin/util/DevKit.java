package com.zrlog.admin.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;

public class DevKit {


    public static void configDev(ServerConfig serverConfig) {
        serverConfig.addLocalFileStaticResourceMapper(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH, PathUtil.getRootPath(), true);
        if (EnvKit.isLambda()) {
            serverConfig.addLocalFileStaticResourceMapper(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH + "/lambda", "/tmp", true);
        }
    }
}
