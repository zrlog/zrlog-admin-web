package com.zrlog.admin.web.config;

import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.common.Constants;

public class AdminRouters {

    public static void configAdminRoute(Router router) {
        router.addMapper("/api/admin/assistant", AutomationController.class, "index");
        router.addMapper("/api/admin/website/assistant", AutomationController.class, "configuration");
        router.addMapper("/api/admin/assistant/save", AutomationController.class, "save");
        router.addMapper("/api/admin/assistant/propose", AutomationController.class, "propose");
        router.addMapper("/api/admin/assistant/enabled", AutomationController.class, "enabled");
        router.addMapper("/api/admin/assistant/delete", AutomationController.class, "delete");
        router.addMapper("/api/admin/assistant/run", AutomationController.class, "run");
        router.addMapper("/api/admin/assistant/runs", AutomationController.class, "runs");
        router.addMapper("/api/admin/assistant/detail", AutomationController.class, "detail");
        router.addMapper("/api/admin/assistant/notices", AutomationController.class, "notices");
        router.addMapper("/api/admin/assistant/readNotice", AutomationController.class, "readNotice");
        router.addMapper("/api/admin/assistant/settings", AutomationController.class, "settings");
        router.addMapper("/api/admin/assistant/settings/save", AutomationController.class, "saveSettings");
        router.addMapper("/api/admin/assistant/token/rotate", AutomationController.class, "rotateToken");
        router.addMapper("/api/admin/assistant/token/revoke", AutomationController.class, "revokeToken");
        // api
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH, AdminController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/403", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/404", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/500", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/offline", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/plugin", AdminController.class, "plugin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/plugins/install", PluginUploadController.class, "install");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/plugins/upload", PluginUploadController.class, "upload");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/index", AdminController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/index/config", AdminController.class, "indexConfig");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/index/first-use/dismiss",
                AdminController.class, "dismissFirstUseChecklist");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website", WebSiteController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/article-edit", WebSiteController.class, "articleEdit");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/content-protector", WebSiteController.class, "contentProtector");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/template", TemplateController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/lab", WebSiteController.class, "lab");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template", TemplateController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template-config", TemplateController.class, "configParams");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template-center", TemplateController.class, "templateCenter");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center", MessageCenterController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center/read", MessageCenterController.class, "read");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center/operation/upgrade-restart", MessageCenterController.class, "upgradeRestart");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upgrade", UpgradeController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upgrade/notice", UpgradeController.class, "notice");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upgrade/docker-status", UpgradeController.class, "dockerStatus");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/system", AdminSystemController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/system/upgrade", WebSiteController.class, "upgrade");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/system/version", WebSiteController.class, "version");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/system/proxy", AdminProxyController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_DEV_URI_BASE_PATH, AdminDevController.class);

        router.addMapper(Constants.API_PUBLIC_ADMIN_RESOURCE, AdminPublicController.class, "adminResource");
        router.addMapper(Constants.API_PUBLIC_VERSION, AdminPublicController.class, "version");
    }
}
