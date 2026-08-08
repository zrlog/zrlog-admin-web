package com.zrlog.admin.web.config;

import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.admin.web.controller.page.AdminTemplatePageController;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.common.Constants;

public class AdminRouters {

    /**
     * 后台管理者路由，这里目前分为2中情况，及服务端响应模板页面和用户对数据的操作
     * 约定
     * 1. 添加页面需要响应时，使用 /admin 的路由，及同步操作
     * 2. 浏览数据，变更数据。使用 /api/admin 的路由，以JSON的格式进行响应，及异步操作
     */
    public static void configAdminRoute(Router router, AdminResource adminResource, String contextPath) {
        // 后台管理者
        router.addMapper(AdminConstants.ADMIN_URI_BASE_PATH, AdminPageController.class);
        adminResource.getAdminPageUris().forEach(uri -> router.addMapper(uri.substring(contextPath.length()), AdminPageController.class, "index"));
        //template download
        router.addMapper(AdminConstants.ADMIN_URI_BASE_PATH + "/template/download", AdminTemplatePageController.class, "download");
        router.addMapper(TemplateInfoHelper.ADMIN_PREVIEW_IMAGE_URI, AdminTemplatePageController.class, "previewImage");
        // api
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH, AdminController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/passkey/authentication/options",
                AdminController.class, "passkeyAuthenticationOptions");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/passkey/authentication/verify",
                AdminController.class, "passkeyAuthenticationVerify");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/403", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/404", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/500", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/offline", AdminController.class, "error");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/link", LinkController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/link-preview", LinkPreviewController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/plugin", AdminController.class, "plugin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/index", AdminController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/index/config", AdminController.class, "indexConfig");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/comment", CommentController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/tag", AdminTagController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/type", TypeController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-type", TypeController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-edit", AdminArticleController.class, "articleEdit");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/cover/generate", AdminArticleController.class, "generateCover");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/cover/apply", AdminArticleController.class, "applyCover");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/context", AdminArticleController.class, "appendAiContext");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/message", AdminArticleController.class, "updateAiMessage");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/messages/clear", AdminArticleController.class, "clearAiMessages");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/messages/export", AdminArticleController.class, "exportAiMessages");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/nav", BlogNavController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article", AdminArticleController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version", AdminArticleVersionController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version/compare", AdminArticleVersionController.class, "compare");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version/rollback", AdminArticleVersionController.class, "rollback");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning", AdminArticlePinningController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/pin", AdminArticlePinningController.class, "pin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/unpin", AdminArticlePinningController.class, "unpin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/move", AdminArticlePinningController.class, "move");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website", WebSiteController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/description/optimize", WebSiteController.class, "optimizeDescription");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/ai/prompt/optimize", WebSiteController.class, "optimizeAiPrompt");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/article-edit", WebSiteController.class, "articleEdit");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/content-protector", WebSiteController.class, "contentProtector");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/webhook", WebhookController.class, "config");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/privacy", PersonalDataController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/template", TemplateController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/lab", WebSiteController.class, "lab");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template", TemplateController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template-config", TemplateController.class, "configParams");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/template-center", TemplateController.class, "templateCenter");
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
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center", MessageCenterController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center/read", MessageCenterController.class, "read");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/message-center/operation/upgrade-restart", MessageCenterController.class, "upgradeRestart");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/personal-data/preview", PersonalDataController.class, "preview");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/personal-data/comments/export", PersonalDataController.class, "exportComments");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook", WebhookController.class, "config");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook/token", WebhookController.class, "token");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/webhook/token/revoke", WebhookController.class, "revokeToken");
        router.addMapper("/api/webhook/message-center/notice", WebhookController.class, "messageCenterNotice");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upgrade", UpgradeController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/upgrade/notice", UpgradeController.class, "notice");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/user", AdminUserController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security", AdminUserController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkeys",
                AdminUserController.class, "passkeys");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/registration/options",
                AdminUserController.class, "passkeyRegistrationOptions");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/registration/verify",
                AdminUserController.class, "passkeyRegistrationVerify");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/remove",
                AdminUserController.class, "passkeyRemove");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/system", AdminSystemController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/static-site", AdminStaticSiteController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_DEV_URI_BASE_PATH, AdminDevController.class);

        router.addMapper(Constants.API_PUBLIC_ADMIN_RESOURCE, AdminPublicController.class, "adminResource");
        router.addMapper(Constants.API_PUBLIC_VERSION, AdminPublicController.class, "version");
    }
}
