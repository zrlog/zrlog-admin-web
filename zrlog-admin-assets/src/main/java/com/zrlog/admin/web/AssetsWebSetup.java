package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.assets.AssetsComponent;
import com.zrlog.admin.web.di.assets.DaggerAssetsComponent;
import com.zrlog.admin.web.interceptor.AdminInterceptor;
import com.zrlog.admin.web.interceptor.AdminTemporaryResourceInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.di.DataComponent;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;

import java.util.Locale;
import java.util.function.Supplier;

public class AssetsWebSetup implements AvatarStorage, AttachmentStorage {
    private final ServerConfig config;
    private final Supplier<PluginCorePlugin> pluginCore;

    public AssetsWebSetup(ServerConfig config, Supplier<PluginCorePlugin> pluginCore) {
        this.config = config;
        this.pluginCore = pluginCore;
    }

    private AssetsComponent components() {
        return config.getBindings().build().get(AssetsComponent.class);
    }

    @Override public void setup() {
        Router router = config.getRouter();
        config.getBindings()
                .singleton(
                        AssetsComponent.class,
                        r ->
                                DaggerAssetsComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                pluginCore,
                                                () -> r.get(TokenService.class),
                                                () ->
                                                        Boolean.TRUE.equals(
                                                                r.get(CacheService.class)
                                                                        .getPublicWebSiteInfo()
                                                                        .getGenerator_html_status()),
                                                config.getContextPath()))
                .singleton(UploadService.class, r -> r.get(AssetsComponent.class).uploads())
                .controller(
                        UploadController.class,
                        (r, request, response) -> r.get(AssetsComponent.class).uploadController())
                .controller(
                        FileManagerController.class,
                        (r, request, response) ->
                                r.get(AssetsComponent.class).fileManagerController())
                .interceptor(
                        AdminTemporaryResourceInterceptor.class,
                        r -> r.get(AssetsComponent.class).temporaryResources());
        config.getInterceptors()
                .add(
                        config.getInterceptors().indexOf(AdminInterceptor.class),
                        AdminTemporaryResourceInterceptor.class);
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
        return components()
                .uploads()
                .saveBytes(bytes, extension, "image", request, AdminTokenThreadLocal.getUser())
                .getUrl();
    }
    @Override public String saveAttachment(byte[] bytes, String filename, com.hibegin.http.server.api.HttpRequest request) throws Exception {
        components().permissions().require(AccountAction.ASSET_UPLOAD);
        if (ZrLogUtil.isPreviewMode()) throw new PermissionErrorException();
        String extension =
                filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,16}")) throw new ArgsException("filename");
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile("zrlog-mcp-upload-", "." + extension);
        try {
            java.nio.file.Files.write(temporary, bytes);
            return components()
                    .uploads()
                    .saveUploadedFile(
                            temporary.toFile(),
                            "mcp",
                            null,
                            request,
                            AdminTokenThreadLocal.getUser())
                    .getUrl();
        } finally { java.nio.file.Files.deleteIfExists(temporary); }
    }

}
