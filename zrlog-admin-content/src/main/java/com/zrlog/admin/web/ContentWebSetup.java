package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.SwsBindings;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.controller.api.AdminTagController;
import com.zrlog.admin.web.controller.api.BlogNavController;
import com.zrlog.admin.web.controller.api.CommentController;
import com.zrlog.admin.web.controller.api.LinkController;
import com.zrlog.admin.web.controller.api.PersonalDataController;
import com.zrlog.admin.web.controller.api.TypeController;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.common.CacheService;

public class ContentWebSetup implements com.zrlog.web.WebSetup {
    private final Router router;
    private final SwsBindings bindings;
    private final java.util.function.Supplier<com.zrlog.admin.business.content.ArticleAssistant> assistant;
    private final java.util.function.Supplier<com.zrlog.admin.business.content.AttachmentStorage> storage;
    public ContentWebSetup(ServerConfig config,
                           java.util.function.Supplier<com.zrlog.admin.business.content.ArticleAssistant> assistant,
                           java.util.function.Supplier<com.zrlog.admin.business.content.AttachmentStorage> storage) {
        this.assistant = assistant;
        this.storage = storage;
        this.router = config.getRouter();
        this.bindings = config.getBindings();
    }
    @Override public void setup() {
        bindings.singleton(AdminArticleService.class, r -> new AdminArticleService(r.get(CacheService.class), assistant, r.get(AdminPageDataFactory.class)))
                .controller(AdminTagController.class, (r, request, response) -> new AdminTagController(r.get(AdminPageDataFactory.class), () -> r.get(CacheService.class).getPublicWebSiteInfo()))
                .controller(TypeController.class, (r, request, response) -> new TypeController(r.get(AdminPageDataFactory.class), () -> r.get(CacheService.class).getPublicWebSiteInfo()))
                .controller(LinkController.class, (r, request, response) -> new LinkController(r.get(AdminPageDataFactory.class)))
                .controller(CommentController.class, (r, request, response) -> new CommentController(r.get(AdminPageDataFactory.class)))
                .controller(PersonalDataController.class, (r, request, response) -> new PersonalDataController(r.get(AdminPageDataFactory.class)))
                .controller(BlogNavController.class, (r, request, response) -> new BlogNavController(r.get(AdminPageDataFactory.class)))
                .singleton(ArticlePublishingService.class, r -> new ArticlePublishingService(r.get(AdminArticleService.class), r.get(CacheRefreshService.class)))
                .singleton(ContentToolService.class, r -> new ContentToolService(r.get(ArticlePublishingService.class)))
                .singleton(com.zrlog.admin.business.knowledge.ContentToolFactory.class, r -> new com.zrlog.admin.business.knowledge.ContentToolFactory(r.get(ContentToolService.class), storage, r.get(com.zrlog.admin.util.BackendServerUrl.class)::configured, () -> Boolean.TRUE.equals(r.get(CacheService.class).getPublicWebSiteInfo().getGenerator_html_status())))
                .controller(AdminArticleVersionController.class, (r, request, response) -> new AdminArticleVersionController(r.get(AdminArticleService.class), r.get(CacheRefreshService.class)))
                .controller(AdminArticleController.class, (r, request, response) -> new AdminArticleController(r.get(AdminArticleService.class), r.get(ArticlePublishingService.class), r.get(AdminStaticSiteSsePublisher.class), assistant, r.get(AdminPageDataFactory.class)));
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
