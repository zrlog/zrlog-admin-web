package com.zrlog.admin.web;

import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.*;

public class AssetsWebSetup implements com.zrlog.admin.business.account.AvatarStorage {
    private final com.hibegin.http.server.config.ServerConfig config;
    public AssetsWebSetup(com.hibegin.http.server.config.ServerConfig config) { this.config = config; }
    @Override public void setup() {
        Router router = config.getRouter();
        config.getInterceptors().add(config.getInterceptors().indexOf(com.zrlog.admin.web.interceptor.AdminInterceptor.class),
                com.zrlog.admin.web.interceptor.AdminTemporaryResourceInterceptor.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upload", UploadController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upload/thumbnail", UploadController.class, "thumbnail");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager", FileManagerController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/rename", FileManagerController.class, "rename");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/mkdir", FileManagerController.class, "mkdir");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/article-resource-url/replace", FileManagerController.class, "replaceArticleResourceUrl");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/reupload", FileManagerController.class, "reuploadMissingLocalResource");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/search", FileManagerController.class, "search");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/roots", FileManagerController.class, "roots");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/read", FileManagerController.class, "readContent");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/file-manager/download", FileManagerController.class, "download");
    }

    @Override public String saveAvatar(byte[] bytes, String extension, com.hibegin.http.server.api.HttpRequest request) {
        return new com.zrlog.admin.business.service.UploadService().saveBytes(bytes, extension, "image", request,
                com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()).getUrl();
    }
}
