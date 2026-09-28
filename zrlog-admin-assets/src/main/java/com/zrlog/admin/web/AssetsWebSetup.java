package com.zrlog.admin.web;

import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.*;

public class AssetsWebSetup implements com.zrlog.admin.business.account.AvatarStorage, com.zrlog.admin.business.content.AttachmentStorage {
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
    @Override public String saveAttachment(byte[] bytes, String filename, com.hibegin.http.server.api.HttpRequest request) throws Exception {
        com.zrlog.admin.business.service.AccountPermissionService.require(com.zrlog.data.security.AccountAction.ASSET_UPLOAD);
        if (com.zrlog.util.ZrLogUtil.isPreviewMode()) throw new com.zrlog.admin.business.exception.PermissionErrorException();
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,16}")) throw new com.zrlog.common.exception.ArgsException("filename");
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile("zrlog-mcp-upload-", "." + extension);
        try {
            java.nio.file.Files.write(temporary, bytes);
            return new com.zrlog.admin.business.service.UploadService().saveUploadedFile(temporary.toFile(), "mcp", null,
                    request, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()).getUrl();
        } finally { java.nio.file.Files.deleteIfExists(temporary); }
    }

}
