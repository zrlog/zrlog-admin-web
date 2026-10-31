package com.zrlog.admin.util;

import com.google.gson.Gson;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.rest.response.AdminSsePayloads;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminSseEmitter {

    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger(AdminSseEmitter.class.getName());
    private final PipedOutputStream outputStream;

    public AdminSseEmitter(PipedOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public static void setHeaders(HttpResponse response) {
        response.getHeader().put("Content-Type", "text/event-stream;charset=UTF-8");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("Connection", "keep-alive");
        response.addHeader("X-Accel-Buffering", "no");
    }

    public static void write(HttpResponse response, String threadName, SseStreamWriter writer) throws IOException {
        write(response, threadName, "sse-error", writer);
    }

    public static void write(HttpResponse response, String threadName, String errorEvent, SseStreamWriter writer)
            throws IOException {
        setHeaders(response);
        PipedInputStream inputStream = new PipedInputStream();
        PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        com.zrlog.common.vo.AdminTokenVO actor = com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser();
        String language = I18nUtil.getCurrentLocale();
        AdminSseEmitter emitter = new AdminSseEmitter(outputStream);
        java.util.concurrent.Callable<Void> streamWriter = com.zrlog.admin.business.security.DelegatedAccess.capture(() -> {
            writer.write(emitter);
            return null;
        });
        Thread streamThread = new Thread(() -> {
            try (AdminLanguageContext ignored = AdminLanguageContext.open(language)) {
                try {
                    com.zrlog.admin.web.token.AdminTokenThreadLocal.withUser(actor, streamWriter);
                } catch (Exception e) {
                    emitter.sendError(errorEvent, e);
                }
            } finally {
                emitter.close();
            }
        }, threadName);
        streamThread.start();
        response.write(inputStream);
    }

    public void send(String event, Object data) throws IOException {
        String payload = "event: " + event + "\n" + "data: " + GSON.toJson(data) + "\n\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public void sendError(String event, Exception e) {
        LOGGER.log(Level.WARNING, "Admin stream step failed: " + event, e);
        try {
            Throwable cause = AdminErrorMessages.unwrap(e);
            String fallbackKey = "static-error".equals(event) ? "admin.staticSite.sync.error"
                    : "publish-error".equals(event) ? "admin.article.publish.error" : "admin.common.request.error";
            String message = AdminErrorMessages.message(cause, fallbackKey);
            if (cause instanceof AbstractAdminBusinessException) {
                AbstractAdminBusinessException businessError = (AbstractAdminBusinessException) cause;
                send(event, AdminSsePayloads.error(businessError.getError(), message));
            } else {
                send(event, AdminSsePayloads.message(message));
            }
        } catch (IOException ignored) {
            // Client connection may already be closed.
        }
    }

    private void close() {
        try {
            outputStream.close();
        } catch (IOException ignored) {
            // Client connection may already be closed.
        }
    }

    @FunctionalInterface
    public interface SseStreamWriter {

        void write(AdminSseEmitter emitter) throws Exception;
    }
}
