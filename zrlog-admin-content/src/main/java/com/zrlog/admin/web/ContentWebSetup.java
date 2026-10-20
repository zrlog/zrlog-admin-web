package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.SwsBindings;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.business.service.ArticleQueryService;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.web.WebSetup;

public class ContentWebSetup implements WebSetup {
    private final Router router;
    private final SwsBindings bindings;

    public ContentWebSetup(ServerConfig config) {
        this.router = config.getRouter();
        this.bindings = config.getBindings();
    }

    @Override
    public void setup() {
        bindings.singleton(AdminArticleService.class, r -> r.get(ContentComponent.class).articles())
                .singleton(ArticleQueryService.class, r -> r.get(ContentComponent.class).queries())
                .singleton(
                        ArticlePublishingService.class,
                        r -> r.get(ContentComponent.class).publishing())
                .singleton(
                        ContentToolService.class, r -> r.get(ContentComponent.class).contentTools())
                .singleton(ContentToolFactory.class, r -> r.get(ContentComponent.class).tools())
                .controller(
                        AdminArticleController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).adminArticleController())
                .controller(
                        AdminArticlePinningController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).adminArticlePinningController())
                .controller(
                        AdminArticleVersionController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).adminArticleVersionController())
                .controller(
                        AdminTagController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).adminTagController())
                .controller(
                        BlogNavController.class,
                        (r, request, response) -> r.get(ContentComponent.class).blogNavController())
                .controller(
                        CommentController.class,
                        (r, request, response) -> r.get(ContentComponent.class).commentController())
                .controller(
                        LinkController.class,
                        (r, request, response) -> r.get(ContentComponent.class).linkController())
                .controller(
                        LinkPreviewController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).linkPreviewController())
                .controller(
                        PersonalDataController.class,
                        (r, request, response) ->
                                r.get(ContentComponent.class).personalDataController())
                .controller(
                        TypeController.class,
                        (r, request, response) -> r.get(ContentComponent.class).typeController());
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
