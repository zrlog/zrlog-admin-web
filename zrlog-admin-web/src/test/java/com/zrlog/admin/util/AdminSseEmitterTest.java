package com.zrlog.admin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.ArticlePinningService;
import com.zrlog.admin.business.service.ArticleVersionService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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

    @Test
    public void shouldPreserveBusinessErrorCodeBeforeArticleIsSaved() throws Exception {
        CapturedResponse response = new CapturedResponse();
        AdminSseEmitter.write(
                response.response(),
                "publish-conflict",
                "publish-error",
                emitter -> {
                    throw new UpdateArticleExpireException();
                });
        String body = response.body();
        assertTrue(body.contains("event: publish-error"));
        assertTrue(body.contains("\"error\":9094"));
        org.junit.Assert.assertFalse(body.contains("event: article"));
        org.junit.Assert.assertFalse(body.contains("event: publish-complete"));
    }

    @Test public void sseWorkerCannotLoseDelegatedPublicationRestrictions() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            CapturedResponse response = new CapturedResponse();
            var identity = new OAuthModels.Identity();
            identity.permissions = java.util.List.of("article.create"); identity.permissionMode = "custom";
            DelegatedAccess.withPermissions(
                    java.util.Set.copyOf(identity.permissions),
                    "inherit".equals(identity.permissionMode),
                    () -> {
                        AdminSseEmitter.write(
                                response.response(),
                                "restricted-publish",
                                emitter -> {
                                    var request = new CreateArticleRequest();
                                    request.setTitle("Must not publish");
                                    request.setTypeId(1L);
                                    request.setRubbish(false);
                                    request.setContent("content");
                                    org.junit.Assert.assertThrows(
                                            PermissionErrorException.class,
                                            () ->
                                                    new AdminArticleService(
                                                                    db.cacheService(),
                                                                    ApplicationServicesFixture
                                                                            .capability(
                                                                                    db.config(),
                                                                                    ArticleAssistant
                                                                                            .class),
                                                                    new AdminPageDataFactory(
                                                                            ApplicationServicesFixture
                                                                                    .site(
                                                                                            db
                                                                                                    .config()),
                                                                            ApplicationServicesFixture
                                                                                    .buildId(
                                                                                            db
                                                                                                    .resources()),
                                                                            ApplicationServicesFixture
                                                                                    .messages(db.dataSource()),
                                                                            AccountServicesFixture
                                                                                    .component(db.dataSource())
                                                                                    .permissions()),
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .permissions(),
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .preferences(),
                                                                    new ArticleVersionService(
                                                                            AccountServicesFixture
                                                                                    .component(db.dataSource())
                                                                                    .permissions(),
                                                                            () -> new Log(db.dataSource()),
                                                                            () ->
                                                                                    new LogVersion(
                                                                                            db.dataSource())),
                                                                    new ArticlePinningService(
                                                                            () -> new Log(db.dataSource()),
                                                                            ApplicationServicesFixture
                                                                                    .locks(db.dataSource())),
                                                                    () -> new Log(db.dataSource()),
                                                                    ApplicationServicesFixture
                                                                            .locks(db.dataSource()))
                                                            .create(
                                                                    AdminTokenThreadLocal.getUser(),
                                                                    request));
                                    emitter.send("checked", true);
                                });
                        return null;
                    });
            assertTrue(response.body().contains("event: checked"));
            org.junit.Assert.assertFalse(DelegatedAccess.active());
            assertTrue(AccountServicesFixture.component(db.dataSource()).permissions().current().canPublish());
        }
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
