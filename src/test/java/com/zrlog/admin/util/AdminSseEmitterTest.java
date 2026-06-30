package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpResponse;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminSseEmitterTest {

    @Test
    public void shouldWriteSseHeadersAndPayload() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();

        AdminSseEmitter.write(capturedResponse.response(), "admin-sse-test",
                emitter -> emitter.send("admin-progress", Map.of("status", "running")));

        String body = capturedResponse.body();
        assertEquals("text/event-stream;charset=UTF-8", capturedResponse.headers.get("Content-Type"));
        assertEquals("no-cache", capturedResponse.addedHeaders.get("Cache-Control"));
        assertEquals("keep-alive", capturedResponse.addedHeaders.get("Connection"));
        assertEquals("no", capturedResponse.addedHeaders.get("X-Accel-Buffering"));
        assertTrue(body.contains("event: admin-progress"));
        assertTrue(body.contains("\"status\":\"running\""));
    }

    @Test
    public void shouldWriteSseErrorWhenWriterFails() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();

        AdminSseEmitter.write(capturedResponse.response(), "admin-sse-error-test", "admin-error",
                emitter -> {
                    throw new IllegalStateException("boom");
                });

        String body = capturedResponse.body();
        assertTrue(body.contains("event: admin-error"));
        assertTrue(body.contains("\"message\":\"boom\""));
    }

    private static class CapturedResponse {
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> addedHeaders = new HashMap<>();
        private InputStream written;

        private String body() throws Exception {
            return new String(written.readAllBytes(), StandardCharsets.UTF_8);
        }

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminSseEmitterTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("getHeader".equals(method.getName())) {
                            return headers;
                        }
                        if ("addHeader".equals(method.getName())) {
                            addedHeaders.put(args[0].toString(), args[1].toString());
                            return null;
                        }
                        if ("write".equals(method.getName())) {
                            if (args[0] instanceof InputStream) {
                                written = (InputStream) args[0];
                            }
                            return null;
                        }
                        if ("write".equals(method.getName()) && args[0] instanceof ByteArrayOutputStream) {
                            return null;
                        }
                        if ("toString".equals(method.getName())) {
                            return "HttpResponseProxy";
                        }
                        return null;
                    });
        }
    }
}
