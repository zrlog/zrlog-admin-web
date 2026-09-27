package com.zrlog.admin.web;

import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.*;

public class ContentWebSetup implements com.zrlog.web.WebSetup {
    private final Router router;
    public ContentWebSetup(Router router) { this.router = router; }
    @Override public void setup() {
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/link", LinkController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/link-preview", LinkPreviewController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/comment", CommentController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/tag", AdminTagController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/type", TypeController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-type", TypeController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-edit", AdminArticleController.class, "articleEdit");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/nav", BlogNavController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article", AdminArticleController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version", AdminArticleVersionController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version/compare", AdminArticleVersionController.class, "compare");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-version/rollback", AdminArticleVersionController.class, "rollback");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning", AdminArticlePinningController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/pin", AdminArticlePinningController.class, "pin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/unpin", AdminArticlePinningController.class, "unpin");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article-pinning/move", AdminArticlePinningController.class, "move");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/privacy", PersonalDataController.class, "index");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/personal-data/preview", PersonalDataController.class, "preview");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/personal-data/comments/export", PersonalDataController.class, "exportComments");
    }
}
