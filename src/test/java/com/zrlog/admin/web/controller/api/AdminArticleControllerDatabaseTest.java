package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.ArticlePageData;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.business.rest.response.PublishCheckToolPayload;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.ArticlePinningService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdminArticleControllerDatabaseTest {

    @After
    public void tearDown() {
        AdminTokenThreadLocal.remove();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateUpdateListAndLoadDraftArticleThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Draft Title", "draft-title", true), createResponse).create();
            AdminPageDataResponse<ArticleGlobalResponse> created =
                    (AdminPageDataResponse<ArticleGlobalResponse>) createResponse.rendered;
            Long logId = Long.valueOf(created.getData().getArticle().getLogId());

            ResponseRecorder updateResponse = new ResponseRecorder();
            controller(Map.of(), updateBody(logId.intValue(), "Updated Draft", "updated-draft"), updateResponse)
                    .update();
            AdminPageDataResponse<ArticleGlobalResponse> updated =
                    (AdminPageDataResponse<ArticleGlobalResponse>) updateResponse.rendered;
            AdminPageDataResponse<ArticlePageData> page =
                    controller(Map.of("status", "draft", "size", "5"), null, new ResponseRecorder()).index();
            AdminPageDataResponse<ArticleGlobalResponse> edit =
                    controller(Map.of("id", logId.toString()), null, new ResponseRecorder()).articleEdit();
            AdminPageDataResponse<LoadEditArticleResponse> detail =
                    controller(Map.of("id", logId.toString()), null, new ResponseRecorder()).detail();

            assertEquals("Draft Title", created.getData().getArticle().getTitle());
            assertEquals("Updated Draft", updated.getData().getArticle().getTitle());
            assertEquals("updated-draft", updated.getData().getArticle().getAlias());
            assertEquals(1, page.getData().getRows().size());
            assertEquals("Updated Draft", page.getData().getRows().get(0).getTitle());
            assertEquals("Updated Draft", edit.getData().getArticle().getTitle());
            assertEquals("Updated Draft", detail.getData().getTitle());
            assertEquals("Updated Draft", db.queryOne("select title from log where logId=?", logId).get("title"));
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("CREATE_ARTICLE"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldHandleMissingMarkdownContentForCurrentJvmAndPreserveExplicitHtml() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            String markdownOnly = "{"
                    + "\"title\":\"Rendered Draft\","
                    + "\"alias\":\"rendered-draft\","
                    + "\"markdown\":\"# Rendered\\n\\nfirst line\\nsecond line\","
                    + "\"typeId\":1,"
                    + "\"canComment\":true,"
                    + "\"recommended\":false,"
                    + "\"privacy\":false,"
                    + "\"rubbish\":true,"
                    + "\"transparentPublish\":false,"
                    + "\"editorType\":\"markdown\""
                    + "}";
            ResponseRecorder renderedResponse = new ResponseRecorder();

            controller(Map.of(), markdownOnly, renderedResponse).create();

            AdminPageDataResponse<ArticleGlobalResponse> rendered =
                    (AdminPageDataResponse<ArticleGlobalResponse>) renderedResponse.rendered;
            Long renderedId = Long.valueOf(rendered.getData().getArticle().getLogId());
            assertEquals(Runtime.version().feature() >= 17
                            ? "<h1>Rendered</h1>\n<p>first line<br>second line</p>\n" : null,
                    db.queryOne("select content from log where logId=?", renderedId).get("content"));
            assertEquals("# Rendered\n\nfirst line\nsecond line",
                    db.queryOne("select markdown from log where logId=?", renderedId).get("markdown"));

            String markdownUpdate = "{"
                    + "\"logId\":" + renderedId + ","
                    + "\"version\":0,"
                    + "\"title\":\"Updated Markdown\","
                    + "\"alias\":\"updated-markdown\","
                    + "\"content\":\"\","
                    + "\"markdown\":\"## Updated\\n\\nnew body\","
                    + "\"typeId\":1,"
                    + "\"canComment\":true,"
                    + "\"recommended\":false,"
                    + "\"privacy\":false,"
                    + "\"rubbish\":true,"
                    + "\"transparentPublish\":false,"
                    + "\"editorType\":\"markdown\""
                    + "}";
            controller(Map.of(), markdownUpdate, new ResponseRecorder()).update();
            assertEquals(Runtime.version().feature() >= 17 ? "<h2>Updated</h2>\n<p>new body</p>\n" : "",
                    db.queryOne("select content from log where logId=?", renderedId).get("content"));
            assertEquals("## Updated\n\nnew body",
                    db.queryOne("select markdown from log where logId=?", renderedId).get("markdown"));

            ResponseRecorder explicitResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Explicit HTML", "explicit-html", true), explicitResponse).create();
            AdminPageDataResponse<ArticleGlobalResponse> explicit =
                    (AdminPageDataResponse<ArticleGlobalResponse>) explicitResponse.rendered;
            Long explicitId = Long.valueOf(explicit.getData().getArticle().getLogId());
            assertEquals("<p>Explicit HTML content</p>",
                    db.queryOne("select content from log where logId=?", explicitId).get("content"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCreateAndUpdatePublishedArticleWithRefreshCachePathThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Published Title", "published-title", false), createResponse).create();
            AdminPageDataResponse<ArticleGlobalResponse> created =
                    (AdminPageDataResponse<ArticleGlobalResponse>) createResponse.rendered;
            Long logId = Long.valueOf(created.getData().getArticle().getLogId());

            ResponseRecorder updateResponse = new ResponseRecorder();
            controller(Map.of(), updateBody(logId.intValue(), "Published Updated", "published-updated", false),
                    updateResponse).update();
            AdminPageDataResponse<ArticleGlobalResponse> updated =
                    (AdminPageDataResponse<ArticleGlobalResponse>) updateResponse.rendered;
            Map<String, Object> row = db.queryOne("select title,alias,rubbish,privacy from log where logId=?", logId);
            String auditLog = String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value"));

            assertEquals("Published Title", created.getData().getArticle().getTitle());
            assertEquals("Published Updated", updated.getData().getArticle().getTitle());
            assertEquals("Published Updated", row.get("title"));
            assertEquals("published-updated", row.get("alias"));
            assertEquals(false, row.get("rubbish"));
            assertEquals(false, row.get("privacy"));
            assertTrue(auditLog.contains("CREATE_ARTICLE"));
            assertTrue(auditLog.contains("UPDATE_ARTICLE"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRefreshBlogWhenPinnedArticleMovesToDraft() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Pinned Published", "pinned-published", false), createResponse)
                    .create();
            AdminPageDataResponse<ArticleGlobalResponse> created =
                    (AdminPageDataResponse<ArticleGlobalResponse>) createResponse.rendered;
            Long logId = Long.valueOf(created.getData().getArticle().getLogId());
            new ArticlePinningService().pin(logId);
            int refreshCountBeforeDraft = db.cacheService().getRefreshCount();

            controller(Map.of(), updateBody(logId.intValue(), "Pinned Draft", "pinned-draft", true),
                    new ResponseRecorder()).update();

            assertEquals(refreshCountBeforeDraft + 1, db.cacheService().getRefreshCount());
            assertEquals(0, ((Number) db.scalar("select sticky from log where logId=?", logId)).intValue());
        }
    }

    @Test
    public void shouldCreatePublishedArticleWithTransparentPublishStreamThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder response = new ResponseRecorder();

            controller(Map.of(), articleBody("Stream Published", "stream-published", false, true), response)
                    .create();
            Map<String, Object> row = db.queryOne(
                    "select title,alias,rubbish,privacy from log where alias=?", "stream-published");

            assertEquals("Stream Published", row.get("title"));
            assertEquals("stream-published", row.get("alias"));
            assertEquals(false, row.get("rubbish"));
            assertEquals(false, row.get("privacy"));
            assertTrue(response.writtenBody.contains("event: article"));
            assertTrue(response.writtenBody.contains("event: publish-start"));
            assertTrue(response.writtenBody.contains("event: publish-complete"));
            assertTrue(response.writtenBody.contains("Stream Published"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRenderExistingPublishedArticleAsTransparentPublishStreamThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Render Stream", "render-stream", false), createResponse).create();
            AdminPageDataResponse<ArticleGlobalResponse> created =
                    (AdminPageDataResponse<ArticleGlobalResponse>) createResponse.rendered;
            CreateArticleRequest request = articleRequest("Render Stream");
            CreateOrUpdateArticleResponse saveResponse = new CreateOrUpdateArticleResponse();
            saveResponse.setLogId(Long.valueOf(created.getData().getArticle().getLogId()));
            saveResponse.setRubbish(false);
            saveResponse.setPrivacy(false);
            ResponseRecorder response = new ResponseRecorder();

            invoke(controller(Map.of(), null, response), "renderArticleSaveResponse", request, saveResponse);

            assertTrue(response.writtenBody.contains("event: article"));
            assertTrue(response.writtenBody.contains("event: publish-start"));
            assertTrue(response.writtenBody.contains("event: publish-complete"));
            assertTrue(response.writtenBody.contains("Render Stream"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUpdatePublishedArticleWithTransparentPublishStreamThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Before Stream Update", "before-stream-update", false), createResponse)
                    .create();
            AdminPageDataResponse<ArticleGlobalResponse> created =
                    (AdminPageDataResponse<ArticleGlobalResponse>) createResponse.rendered;
            Long logId = Long.valueOf(created.getData().getArticle().getLogId());
            ResponseRecorder response = new ResponseRecorder();

            controller(Map.of(), updateBody(logId.intValue(), "After Stream Update", "after-stream-update",
                    false, true), response).update();
            Map<String, Object> row = db.queryOne(
                    "select title,alias,rubbish,privacy from log where logId=?", logId);

            assertEquals("After Stream Update", row.get("title"));
            assertEquals("after-stream-update", row.get("alias"));
            assertEquals(false, row.get("rubbish"));
            assertEquals(false, row.get("privacy"));
            assertTrue(response.writtenBody.contains("event: article"));
            assertTrue(response.writtenBody.contains("event: publish-start"));
            assertTrue(response.writtenBody.contains("event: publish-complete"));
            assertTrue(response.writtenBody.contains("After Stream Update"));
        }
    }

    @Test
    public void shouldUseWebsiteConfiguredArticlePageSizeWhenRequestSizeIsMissing() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            db.putWebsite("admin_article_page_size", 1);
            controller(Map.of(), articleBody("First Draft", "first-draft", true), new ResponseRecorder()).create();
            controller(Map.of(), articleBody("Second Draft", "second-draft", true), new ResponseRecorder()).create();

            AdminPageDataResponse<ArticlePageData> page =
                    controller(Map.of("status", "draft"), null, new ResponseRecorder()).index();

            assertEquals(2L, page.getData().getTotalElements());
            assertEquals(1, page.getData().getRows().size());
        }
    }

    @Test
    public void shouldUseDefaultArticlePageSizeWhenWebsiteConfigIsBlank() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            db.putWebsite("admin_article_page_size", "");
            controller(Map.of(), articleBody("Default Size Draft", "default-size-draft", true),
                    new ResponseRecorder()).create();

            AdminPageDataResponse<ArticlePageData> page =
                    controller(Map.of("status", "draft"), null, new ResponseRecorder()).index();

            assertEquals(1L, page.getData().getTotalElements());
            assertEquals(1, page.getData().getRows().size());
        }
    }

    @Test
    public void shouldDeleteArticleThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ResponseRecorder createResponse = new ResponseRecorder();
            controller(Map.of(), articleBody("Delete Draft", "delete-draft", true), createResponse).create();
            AdminPageDataResponse<?> created = (AdminPageDataResponse<?>) createResponse.rendered;
            ArticleGlobalResponse data = (ArticleGlobalResponse) created.getData();
            String id = data.getArticle().getLogId().toString();

            DeleteResponse deleteResponse = controller(Map.of("id", id), null, new ResponseRecorder()).delete();

            assertTrue(deleteResponse.getData().getDelete());
            assertEquals(null, db.queryOne("select logId from log where logId=?", Long.valueOf(id)));
        }
    }

    @Test
    public void shouldRecordPublishCheckNoticesThroughControllerWrappers() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("admin_cache:message_center_operation_notices", "[]");
            AdminArticleController controller = controller(Map.of(), null, new ResponseRecorder());

            invoke(controller, "recordPublishCheckSuccess", 7L, "Checked Article",
                    Map.of("score", 90, "items", List.of("ok")));
            invoke(controller, "recordPublishCheckError", 8L, "Broken Article", "bad check");
            String stored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "admin_cache:message_center_operation_notices")
                    .get("value"));

            assertTrue(stored.contains("Checked Article"));
            assertTrue(stored.contains("\"score\":90"));
            assertTrue(stored.contains("Broken Article"));
            assertTrue(stored.contains("bad check"));
        }
    }

    @Test
    public void shouldManageArticleAiMessagesThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            AdminArticleController append = controller(Map.of("id", "9"),
                    "{\"title\":\"Article\",\"markdown\":\"Markdown\",\"articleVersion\":2}",
                    new ResponseRecorder());
            ApiStandardResponse<List<AIResponseEntry.AIContentEntry>> appended = append.appendAiContext();
            AIResponseEntry.AIContentEntry message = appended.getData().get(0);

            ApiStandardResponse<Boolean> updated = controller(Map.of("id", "9"),
                    "{\"messageId\":\"" + message.getMessageId() + "\",\"tool\":\"publishCheck\","
                            + "\"payload\":{\"ok\":true}}",
                    new ResponseRecorder()).updateAiMessage();
            ApiStandardResponse<ArticleAIMessageExportResponse> exported =
                    controller(Map.of("id", "9"), null, new ResponseRecorder()).exportAiMessages();
            ApiStandardResponse<Boolean> cleared =
                    controller(Map.of("id", "9"), null, new ResponseRecorder()).clearAiMessages();
            ApiStandardResponse<ArticleAIMessageExportResponse> afterClear =
                    controller(Map.of("id", "9"), null, new ResponseRecorder()).exportAiMessages();

            assertEquals("user", message.getRole());
            assertEquals("articleContext", message.getMessageType());
            assertFalse(message.getMessageId().isEmpty());
            assertTrue(updated.getData());
            assertEquals(2, exported.getData().getMessageCount());
            assertTrue(cleared.getData());
            assertEquals(0, afterClear.getData().getMessageCount());
        }
    }

    @Test
    public void shouldApplyExternalCoverAndUpdateAiMessagePayloadThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            ApiStandardResponse<List<AIResponseEntry.AIContentEntry>> appended = controller(Map.of("id", "9"),
                    "{\"title\":\"Article\",\"markdown\":\"Markdown\",\"articleVersion\":2}",
                    new ResponseRecorder()).appendAiContext();
            String messageId = appended.getData().get(0).getMessageId();

            ApiStandardResponse<UploadFileResponse> applied = controller(Map.of("id", "9"),
                    "{\"dataUrl\":\"/attached/cover.png\",\"extension\":\"png\",\"messageId\":\"" + messageId + "\"}",
                    new ResponseRecorder()).applyCover();
            String stored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_9").get("value"));

            assertEquals("/attached/cover.png", applied.getData().getUrl());
            assertTrue(stored.contains("\"messageId\":\"" + messageId + "\""));
            assertTrue(stored.contains("\"tool\":\"cover\""));
            assertTrue(stored.contains("\"url\":\"/attached/cover.png\""));
        }
    }

    @Test
    public void shouldReturnAiToolConfigurationErrorAsSseResponse() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            ResponseRecorder response = new ResponseRecorder();

            controller(Map.of("id", "9", "input", "check before publish", "tool", "publishCheck"),
                    "{\"title\":\"Article\",\"markdown\":\"Markdown\",\"digest\":\"Digest\",\"keywords\":\"java\"}",
                    response).ai();

            assertEquals(200, response.statusCode);
            assertTrue(response.writtenBody.contains("event: ai-error"));
            assertTrue(response.writtenBody.contains("\"errorType\":\"configuration_required\""));
            assertTrue(response.writtenBody.contains("ai_provider"));
        }
    }

    @Test
    public void shouldShortCircuitPublishCheckAndEmitReadyStatesWithoutCallingAi() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            AdminArticleController controller = controller(Map.of(), null, new ResponseRecorder());
            CreateArticleRequest request = new CreateArticleRequest();
            request.setTransparentPublish(true);
            request.setRubbish(false);
            request.setPrivacy(false);
            ArticleGlobalResponse global = new ArticleGlobalResponse();
            global.setPublishCheckEnabled(false);
            global.setAiConfigured(true);
            AdminPageDataResponse<ArticleGlobalResponse> detail = new AdminPageDataResponse<>(global);

            assertEquals(true, invoke(controller, "shouldUseTransparentPublishStream", request));
            assertNull(invoke(controller, "startPublishCheck", detail, request));

            request.setPrivacy(true);
            global.setPublishCheckEnabled(true);
            global.setAiConfigured(false);

            assertEquals(false, invoke(controller, "shouldUseTransparentPublishStream", request));
            assertNull(invoke(controller, "startPublishCheck", detail, request));

            String successPayload = emitPublishCheck(
                    CompletableFuture.completedFuture(new PublishCheckResponse(
                            new PublishCheckToolPayload("publishCheck", Map.of("ok", true)),
                            null,
                            null,
                            List.of())), false);
            CompletableFuture<PublishCheckResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("publish failed"));
            String errorPayload = emitPublishCheck(failed, true);

            assertTrue(successPayload.contains("event: publish-check-complete"));
            assertTrue(successPayload.contains("\"ok\":true"));
            assertTrue(errorPayload.contains("event: publish-check-error"));
            assertTrue(errorPayload.contains("publish failed"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStartPublishCheckAndRecordConfigurationErrorThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("admin_cache:message_center_operation_notices", "[]");
            AdminArticleController controller = controller(Map.of(), null, new ResponseRecorder());
            CreateArticleRequest request = articleRequest("Publish Check");
            request.setMarkdown("");
            request.setContent("Publish Check content");
            LoadEditArticleResponse article = new LoadEditArticleResponse();
            article.setLogId(42);
            ArticleGlobalResponse global = new ArticleGlobalResponse();
            global.setArticle(article);
            global.setPublishCheckEnabled(true);
            global.setAiConfigured(true);
            AdminPageDataResponse<ArticleGlobalResponse> detail = new AdminPageDataResponse<>(global);

            CompletableFuture<PublishCheckResponse> future = (CompletableFuture<PublishCheckResponse>)
                    invoke(controller, "startPublishCheck", detail, request);

            assertNotNull(future);
            try {
                future.join();
            } catch (CompletionException e) {
                assertTrue(e.getMessage().contains("ArgsException") || e.getCause() != null);
            }
            String stored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "admin_cache:message_center_operation_notices")
                    .get("value"));
            assertTrue(stored.contains("Publish Check"));
            assertTrue(stored.contains("ai_provider"));
        }
    }

    private static String articleBody(String title, String alias, boolean rubbish) {
        return articleBody(title, alias, rubbish, false);
    }

    private static String articleBody(String title, String alias, boolean rubbish, boolean transparentPublish) {
        return "{"
                + "\"title\":\"" + title + "\","
                + "\"alias\":\"" + alias + "\","
                + "\"content\":\"<p>" + title + " content</p>\","
                + "\"markdown\":\"" + title + " markdown\","
                + "\"keywords\":\"java,zrlog\","
                + "\"typeId\":1,"
                + "\"canComment\":true,"
                + "\"recommended\":false,"
                + "\"privacy\":false,"
                + "\"rubbish\":" + rubbish + ","
                + "\"transparentPublish\":" + transparentPublish + ","
                + "\"editorType\":\"markdown\""
                + "}";
    }

    private static CreateArticleRequest articleRequest(String title) {
        CreateArticleRequest request = new CreateArticleRequest();
        request.setTitle(title);
        request.setAlias(title.toLowerCase().replace(" ", "-"));
        request.setContent("<p>" + title + " content</p>");
        request.setMarkdown(title + " markdown");
        request.setDigest("Digest");
        request.setKeywords("java,zrlog");
        request.setTypeId(1L);
        request.setCanComment(true);
        request.setPrivacy(false);
        request.setRubbish(false);
        request.setTransparentPublish(true);
        request.setEditorType("markdown");
        return request;
    }

    private static String updateBody(int logId, String title, String alias) {
        return updateBody(logId, title, alias, true);
    }

    private static String updateBody(int logId, String title, String alias, boolean rubbish) {
        return updateBody(logId, title, alias, rubbish, false);
    }

    private static String updateBody(int logId, String title, String alias, boolean rubbish,
                                     boolean transparentPublish) {
        return "{"
                + "\"logId\":" + logId + ","
                + "\"version\":0,"
                + "\"title\":\"" + title + "\","
                + "\"alias\":\"" + alias + "\","
                + "\"content\":\"<p>" + title + " content</p>\","
                + "\"markdown\":\"" + title + " markdown\","
                + "\"digest\":\"Digest\","
                + "\"keywords\":\"updated,zrlog\","
                + "\"typeId\":1,"
                + "\"canComment\":false,"
                + "\"recommended\":true,"
                + "\"privacy\":false,"
                + "\"rubbish\":" + rubbish + ","
                + "\"transparentPublish\":" + transparentPublish + ","
                + "\"editorType\":\"markdown\""
                + "}";
    }

    private static AdminArticleController controller(Map<String, String> params, String body,
                                                     ResponseRecorder response) throws Exception {
        AdminArticleController controller = new AdminArticleController();
        setControllerField(controller, "request", request(params, body));
        setControllerField(controller, "response", response.response());
        return controller;
    }

    private static void setControllerField(AdminArticleController controller, String name, Object value)
            throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static Object invoke(AdminArticleController controller, String name, Object... args) throws Exception {
        for (Method method : AdminArticleController.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(controller, args);
            }
        }
        throw new IllegalArgumentException("No method " + name);
    }

    private static String emitPublishCheck(CompletableFuture<PublishCheckResponse> future, boolean wait)
            throws Exception {
        AdminArticleController controller = controller(Map.of(), null, new ResponseRecorder());
        try (PipedInputStream inputStream = new PipedInputStream();
             PipedOutputStream outputStream = new PipedOutputStream(inputStream)) {
            AdminSseEmitter emitter = new AdminSseEmitter(outputStream);
            invoke(controller, "sendPublishCheckIfReady", future, new AtomicBoolean(false), emitter, wait);
            outputStream.close();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HttpRequest request(Map<String, String> params, String body) {
        Map<String, Object> attrs = new HashMap<>();
        return (HttpRequest) Proxy.newProxyInstance(
                AdminArticleControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getParaToInt":
                            return Integer.parseInt(params.getOrDefault(args[0].toString(), args[1].toString()));
                        case "decodeParamMap":
                            return Map.of();
                        case "getInputStream":
                            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        case "getUri":
                            return "/api/admin/article";
                        case "getHeader":
                            return "User-Agent".equals(args[0]) ? "JUnit" : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getContextPath":
                            return "/";
                        case "getScheme":
                            return "http";
                        case "getAttr":
                            return attrs;
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static void setAdminToken() throws Exception {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setSessionId("session-1");
        token.setProtocol("http");
        Method method = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        method.setAccessible(true);
        method.invoke(null, token);
    }

    private static class ResponseRecorder {

        private Object rendered;
        private int statusCode = -1;
        private String writtenBody = "";

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminArticleControllerDatabaseTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("renderJson".equals(method.getName())) {
                            rendered = args[0];
                        }
                        if ("write".equals(method.getName()) && args != null && args.length >= 2) {
                            statusCode = (int) args[1];
                            if (args[0] instanceof InputStream) {
                                writtenBody = new String(((InputStream) args[0]).readAllBytes(),
                                        StandardCharsets.UTF_8);
                            }
                        }
                        if ("write".equals(method.getName()) && args != null && args.length == 1
                                && args[0] instanceof InputStream) {
                            writtenBody = new String(((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8);
                        }
                        if ("getHeader".equals(method.getName())) {
                            return new HashMap<String, String>();
                        }
                        if ("addHeader".equals(method.getName())) {
                            return null;
                        }
                        return null;
                    });
        }
    }
}
