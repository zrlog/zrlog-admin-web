package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.SwsBindings;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.ai.service.AIReferenceService;
import com.zrlog.admin.business.content.ArticleConversationBindings;
import com.zrlog.admin.business.content.ArticleEditorDecorator;
import com.zrlog.admin.business.content.ArticlePublishCheck;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.business.service.WebSiteSettingsService;
import com.zrlog.admin.web.controller.ai.AICommentController;
import com.zrlog.admin.web.controller.ai.AIWebSiteController;
import com.zrlog.admin.web.controller.ai.AssistantChatController;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ai.AIComponent;
import com.zrlog.admin.web.di.ai.DaggerAIComponent;
import com.zrlog.admin.web.interceptor.PluginTokenValidator;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.data.di.DataComponent;
import com.zrlog.web.WebSetup;

public class AIWebSetup implements WebSetup {
    private final Router router;

    private final SwsBindings bindings;

    public AIWebSetup(ServerConfig config) {
        this.router = config.getRouter();
        this.bindings = config.getBindings();
    }

    @Override
    public void setup() {
        bindings.singleton(
                        AIComponent.class,
                        r ->
                                DaggerAIComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                r.get(ServiceComponent.class),
                                                r.get(AccountComponent.class),
                                                r.get(ContentToolFactory.class),
                                                r.get(UploadService.class),
                                                r.get(WebSiteSettingsService.class),
                                                r.get(PluginTokenValidator.class),
                                                r.get(
                                                        com.zrlog.admin.business.automation
                                                                .AssistantTaskContextProvider
                                                                .class)))
                .singleton(AIReferenceService.class, r -> r.get(AIComponent.class).references())
                .controller(
                        AssistantChatController.class,
                        (r, request, response) ->
                                r.get(AIComponent.class).requests().create(request).articles())
                .controller(
                        AICommentController.class,
                        (r, request, response) ->
                                r.get(AIComponent.class).requests().create(request).comments())
                .controller(
                        AIWebSiteController.class,
                        (r, request, response) ->
                                r.get(AIComponent.class).requests().create(request).settings());
        registerRoutes(router);
    }

    private AIComponent components() {
        return bindings.build().get(AIComponent.class);
    }

    public ArticleConversationBindings articleConversations() {
        return components().articleConversations();
    }

    public ArticleEditorDecorator articleEditor() {
        return components().articleEditor();
    }

    public ArticlePublishCheck articlePublishCheck() {
        return components().articlePublishCheck();
    }

    private void registerRoutes(Router router) {
        router.addMapper(
                "/api/admin/internal/ai/comment/analyze", AICommentController.class, "analyze");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/cover/apply",
                AssistantChatController.class,
                "applyCover");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/assistant/chat/message",
                AssistantChatController.class,
                "updateAiMessage");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/assistant/chat/messages/clear",
                AssistantChatController.class,
                "clearAiMessages");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/assistant/chat/messages/export",
                AssistantChatController.class,
                "exportAiMessages");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/description/optimize",
                AIWebSiteController.class,
                "optimizeDescription");
        router.addMapper(
                "/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/ai/prompt/optimize",
                AIWebSiteController.class,
                "optimizeAiPrompt");
        router.addMapper("/api/admin/assistant/chat", AssistantChatController.class, "ai");
        router.addMapper(
                "/api/admin/assistant/chat/draft",
                AssistantChatController.class,
                "openDraftConversation");
        router.addMapper(
                "/api/admin/assistant/chat/conversation",
                AssistantChatController.class,
                "conversation");
        router.addMapper(
                "/api/admin/assistant/chat/approval",
                AssistantChatController.class,
                "approveAiOperation");
        router.addMapper(
                "/api/admin/assistant/chat/input", AssistantChatController.class, "submitAiInput");
        router.addMapper(
                "/api/admin/assistant/chat/sessions", AssistantChatController.class, "sessions");
        router.addMapper(
                "/api/admin/assistant/chat/sessions/rename",
                AssistantChatController.class,
                "renameSession");
        router.addMapper(
                "/api/admin/assistant/chat/sessions/delete",
                AssistantChatController.class,
                "deleteSession");
        router.addMapper("/api/admin/assistant/chat/run", AssistantChatController.class, "aiRun");
        router.addMapper("/api/admin/assistant/chat/file", AssistantChatController.class, "aiFile");
        router.addMapper(
                "/api/admin/assistant/chat/references",
                AssistantChatController.class,
                "aiReferences");
        router.addMapper(
                "/api/admin/article/applyCover", AssistantChatController.class, "applyCover");
        router.addMapper(
                "/api/admin/article/updateAiMessage",
                AssistantChatController.class,
                "updateAiMessage");
        router.addMapper(
                "/api/admin/article/clearAiMessages",
                AssistantChatController.class,
                "clearAiMessages");
        router.addMapper(
                "/api/admin/article/exportAiMessages",
                AssistantChatController.class,
                "exportAiMessages");
        router.addMapper("/api/admin/website/ai", AIWebSiteController.class, "ai");
        router.addMapper(
                "/api/admin/website/optimizeDescription",
                AIWebSiteController.class,
                "optimizeDescription");
        router.addMapper(
                "/api/admin/website/optimizeAiPrompt",
                AIWebSiteController.class,
                "optimizeAiPrompt");
    }
}
