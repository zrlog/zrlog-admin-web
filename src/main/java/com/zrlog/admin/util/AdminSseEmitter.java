package com.zrlog.admin.util;

import com.google.gson.Gson;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.rest.response.AdminSsePayloads;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class AdminSseEmitter {

    private static final Gson GSON = new Gson();
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
        Thread streamThread = new Thread(() -> {
            AdminSseEmitter emitter = new AdminSseEmitter(outputStream);
            try {
                writer.write(emitter);
            } catch (Exception e) {
                emitter.sendError(errorEvent, e);
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
        try {
            send(event, AdminSsePayloads.message(Objects.requireNonNullElse(e.getMessage(), "")));
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
