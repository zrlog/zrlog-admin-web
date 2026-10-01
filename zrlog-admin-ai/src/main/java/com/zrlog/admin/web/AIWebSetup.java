package com.zrlog.admin.web;

import com.hibegin.http.server.web.Router;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.ai.service.AIConversationService;
import com.zrlog.admin.business.ai.service.AIPublishCheckService;
import com.zrlog.admin.business.ai.service.AIPublishCheckService.PublishCheckTask;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.web.controller.ai.AIArticleController;
import com.zrlog.admin.web.controller.ai.AIWebSiteController;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.util.I18nUtil;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AIWebSetup implements ArticleAssistant {
    private static final Logger LOGGER = LoggerUtil.getLogger(AIWebSetup.class);
    private static final long PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private final Router router;
    private final AIPublishCheckService publishingService = new AIPublishCheckService();

    public AIWebSetup(Router router) { this.router = router; }
    @Override public void setup() { registerRoutes(router); }

    @Override public void articleCreated(long articleId, int userId) {
        try { new AIConversationService().migrateDraftAIMessageToArticle(articleId, -(long) userId); }
        catch (Exception e) { LOGGER.log(Level.FINE, "Migrate draft article AI messages failed, articleId=" + articleId, e); }
    }

    @Override public void articleDeleted(long articleId) {
        try { new AIConversationService().removeAIMessage(articleId); }
        catch (Exception e) { LOGGER.log(Level.FINE, "Remove article AI messages failed, articleId=" + articleId, e); }
    }

    @Override public ArticleGlobalResponse enrichEditor(ArticleGlobalResponse article, long articleId) {
        AIWebSiteInfoWithAIMessages ai = new AIConversationService().getAiMessageInfoByArticleId(articleId);
        AIArticleGlobalResponse response = new AIArticleGlobalResponse(article);
        response.setAiProvider(ai.getAi_provider());
        response.setAiModel(ai.getAi_model());
        response.setAiConfigured(ai.getAi_provider() != null && StringUtils.isNotEmpty(ai.getAi_model())
                && (StringUtils.isNotEmpty(ai.getAi_api_key()) || StringUtils.isNotEmpty(ai.getAi_base_url())));
        response.setAiMessages(ai.getAiMessages().stream().filter(e -> !Objects.equals(e.getRole(), "system")).collect(Collectors.toList()));
        return response;
    }

    @Override public PublishProgress beginPublishCheck(ArticleGlobalResponse article, CreateArticleRequest request,
                                                       AdminSseEmitter emitter) throws Exception {
        PublishCheckTask task = publishingService.startPublishCheck(article, request);
        if (task == null) return PublishProgress.NONE;
        emitter.send("publish-check-start", AdminSsePayloads.tool("publishCheck"));
        return new PublishProgress() {
            private final AtomicBoolean sent = new AtomicBoolean();
            @Override public void progress(AdminSseEmitter progress) throws Exception {
                sendPublishCheckIfReady(task, sent, progress, false);
            }
            @Override public void complete(AdminSseEmitter progress) throws Exception {
                sendPublishCheckIfReady(task, sent, progress, true);
            }
        };
    }

    private void sendPublishCheckIfReady(PublishCheckTask publishCheckTask,
                                         AtomicBoolean publishCheckSent, AdminSseEmitter emitter, boolean wait)
            throws Exception {
        sendPublishCheckIfReady(publishCheckTask, publishCheckSent, emitter, wait,
                PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS);
    }

    private void sendPublishCheckIfReady(PublishCheckTask publishCheckTask,
                                         AtomicBoolean publishCheckSent, AdminSseEmitter emitter, boolean wait,
                                         long timeoutMillis) throws Exception {
        if (publishCheckTask == null || (!wait && !publishCheckTask.getFuture().isDone())) {
            return;
        }
        if (!publishCheckSent.compareAndSet(false, true)) {
            return;
        }
        try {
            PublishCheckResponse result = wait
                    ? publishCheckTask.getFuture().get(timeoutMillis, TimeUnit.MILLISECONDS)
                    : publishCheckTask.getFuture().join();
            emitter.send("publish-check-complete", result);
        } catch (CompletionException | ExecutionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = com.zrlog.admin.business.ai.service.AIErrorMessages.message(
                    cause, "admin.article.publishCheck.error.failed");
            if (publishCheckTask.getArticleId() != null) {
                publishingService.recordPublishCheckError(
                        publishCheckTask.getArticleId(), publishCheckTask.getArticleTitle(), message);
            }
            emitter.send("publish-check-error", AdminSsePayloads.message(message));
        } catch (CancellationException e) {
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.cancelled");
        } catch (TimeoutException e) {
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendCancelledPublishCheck(publishCheckTask, emitter,
                    "admin.article.publishCheck.error.interrupted");
        }
    }

    private void sendCancelledPublishCheck(PublishCheckTask publishCheckTask, AdminSseEmitter emitter,
                                           String messageKey) throws IOException {
        PublishCheckResponse committedResponse = publishCheckTask.getPersistenceGuard().cancelOrGetCommitted();
        if (committedResponse != null) {
            emitter.send("publish-check-complete", committedResponse);
            return;
        }
        publishCheckTask.getFuture().cancel(true);
        String message = I18nUtil.getAdminBackendStringFromRes(messageKey);
        if (publishCheckTask.getArticleId() != null) {
            publishingService.recordPublishCheckError(
                    publishCheckTask.getArticleId(), publishCheckTask.getArticleTitle(), message);
        }
        emitter.send("publish-check-error", AdminSsePayloads.message(message));
    }

    private void registerRoutes(Router router) {
        router.addMapper("/api/admin/internal/ai/comment/analyze", com.zrlog.admin.web.controller.ai.AICommentController.class, "analyze");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/cover/apply", AIArticleController.class, "applyCover");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/message", AIArticleController.class, "updateAiMessage");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/messages/clear", AIArticleController.class, "clearAiMessages");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/article/ai/messages/export", AIArticleController.class, "exportAiMessages");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/description/optimize", AIWebSiteController.class, "optimizeDescription");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/website/ai/prompt/optimize", AIWebSiteController.class, "optimizeAiPrompt");
        router.addMapper("/api/admin/article/ai", AIArticleController.class, "ai");
        router.addMapper("/api/admin/article/ai/approval", AIArticleController.class, "approveAiOperation");
        router.addMapper("/api/admin/article/ai/input", AIArticleController.class, "submitAiInput");
        router.addMapper("/api/admin/article/ai/run", AIArticleController.class, "aiRun");
        router.addMapper("/api/admin/article/applyCover", AIArticleController.class, "applyCover");
        router.addMapper("/api/admin/article/updateAiMessage", AIArticleController.class, "updateAiMessage");
        router.addMapper("/api/admin/article/clearAiMessages", AIArticleController.class, "clearAiMessages");
        router.addMapper("/api/admin/article/exportAiMessages", AIArticleController.class, "exportAiMessages");
        router.addMapper("/api/admin/website/ai", AIWebSiteController.class, "ai");
        router.addMapper("/api/admin/website/optimizeDescription", AIWebSiteController.class, "optimizeDescription");
        router.addMapper("/api/admin/website/optimizeAiPrompt", AIWebSiteController.class, "optimizeAiPrompt");
    }
}
