package com.zrlog.admin.web;

import com.zrlog.admin.business.ai.service.AIErrorMessages;
import com.zrlog.admin.business.ai.service.AIPublishCheckService;
import com.zrlog.admin.business.ai.service.AIPublishCheckService.PublishCheckTask;
import com.zrlog.admin.business.content.ArticlePublishCheck;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.AdminSsePayloads;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AIArticlePublishCheck implements ArticlePublishCheck {
    private static final long PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private final AIPublishCheckService publishing;

    @Inject
    public AIArticlePublishCheck(AIPublishCheckService publishing) {
        this.publishing = publishing;
    }

    @Override
    public PublishProgress beginPublishCheck(
            AccountAccess actor,
            ArticleGlobalResponse article,
            CreateArticleRequest request,
            AdminSseEmitter emitter)
            throws Exception {
        PublishCheckTask task = publishing.startPublishCheck(actor, article, request);
        if (task == null) return PublishProgress.NONE;
        emitter.send("publish-check-start", AdminSsePayloads.tool("publishCheck"));
        return new PublishProgress() {
            private final AtomicBoolean sent = new AtomicBoolean();

            @Override
            public void progress(AdminSseEmitter progress) throws Exception {
                sendPublishCheckIfReady(task, sent, progress, false);
            }

            @Override
            public void complete(AdminSseEmitter progress) throws Exception {
                sendPublishCheckIfReady(task, sent, progress, true);
            }
        };
    }

    private void sendPublishCheckIfReady(
            PublishCheckTask publishCheckTask,
            AtomicBoolean publishCheckSent,
            AdminSseEmitter emitter,
            boolean wait)
            throws Exception {
        sendPublishCheckIfReady(
                publishCheckTask,
                publishCheckSent,
                emitter,
                wait,
                PUBLISH_CHECK_WAIT_TIMEOUT_MILLIS);
    }

    private void sendPublishCheckIfReady(
            PublishCheckTask publishCheckTask,
            AtomicBoolean publishCheckSent,
            AdminSseEmitter emitter,
            boolean wait,
            long timeoutMillis)
            throws Exception {
        if (publishCheckTask == null || (!wait && !publishCheckTask.getFuture().isDone())) {
            return;
        }
        if (!publishCheckSent.compareAndSet(false, true)) {
            return;
        }
        try {
            PublishCheckResponse result =
                    wait
                            ? publishCheckTask.getFuture().get(timeoutMillis, TimeUnit.MILLISECONDS)
                            : publishCheckTask.getFuture().join();
            emitter.send("publish-check-complete", result);
        } catch (CompletionException | ExecutionException e) {
            Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message =
                    AIErrorMessages.message(cause, "admin.article.publishCheck.error.failed");
            if (publishCheckTask.getArticleId() != null) {
                publishing.recordPublishCheckError(
                        publishCheckTask.getArticleId(),
                        publishCheckTask.getArticleTitle(),
                        "admin.article.publishCheck.error.failed");
            }
            emitter.send("publish-check-error", AdminSsePayloads.message(message));
        } catch (CancellationException e) {
            sendCancelledPublishCheck(
                    publishCheckTask, emitter, "admin.article.publishCheck.error.cancelled");
        } catch (TimeoutException e) {
            sendCancelledPublishCheck(
                    publishCheckTask, emitter, "admin.article.publishCheck.error.timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendCancelledPublishCheck(
                    publishCheckTask, emitter, "admin.article.publishCheck.error.interrupted");
        }
    }

    private void sendCancelledPublishCheck(
            PublishCheckTask publishCheckTask, AdminSseEmitter emitter, String messageKey)
            throws IOException {
        PublishCheckResponse committedResponse =
                publishCheckTask.getPersistenceGuard().cancelOrGetCommitted();
        if (committedResponse != null) {
            emitter.send("publish-check-complete", committedResponse);
            return;
        }
        publishCheckTask.getFuture().cancel(true);
        String message = I18nUtil.getAdminBackendStringFromRes(messageKey);
        if (publishCheckTask.getArticleId() != null) {
            publishing.recordPublishCheckError(
                    publishCheckTask.getArticleId(),
                    publishCheckTask.getArticleTitle(),
                    messageKey);
        }
        emitter.send("publish-check-error", AdminSsePayloads.message(message));
    }
}
