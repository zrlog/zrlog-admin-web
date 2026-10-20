package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.ai.model.AIFileModels;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Explicit actors must survive worker dispatch without installing request identity. */
@RunWith(Parameterized.class)
public class AIExplicitActorBoundaryTest {
    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() { return new Boolean[]{false, true}; }
    private final boolean sqlite;
    public AIExplicitActorBoundaryTest(boolean sqlite) { this.sqlite = sqlite; }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void workerAndPrivateStorageUseExplicitOwnerDespiteAnotherAmbientAccount() throws Exception {
        try (var db = database()) {
            var actor = actor(db);
            var files = new AIFileService(temporary.newFolder().toPath());
            var file = new AIFileModels.Input(); file.name = "note.txt";
            file.data = Base64.getEncoder().encodeToString("Writer document".getBytes(StandardCharsets.UTF_8));
            var input = input(); input.files = List.of(file);
            Model model = new Model(db, files, answer());
            model.afterRequest = () -> {
                assertNull(AdminTokenThreadLocal.getUser());
                assertFalse(DelegatedAccess.active());
                assertEquals(2, model.seenActor.getUserId());
            };
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            String wire = DelegatedAccess.withPermissions(Set.of("article.read"), false,
                    () -> read(model.start(actor, input, audit(), null)));
            assertTrue(wire, wire.contains("\"type\":\"done\""));
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            assertFalse(model.requests.get(0).has("tools")); // Explicit user's knowledge preference is off.
            var conversations = AIServiceDependencies.conversations(db.dataSource());
            assertThrows(PermissionErrorException.class, () -> conversations.exportAIMessage(-2L));
            var stored = conversations.captureAccount(2).exportAIMessage(-2L).getMessages();
            assertEquals(2, stored.stream().filter(message -> Set.of("user", "assistant").contains(message.getRole())).count());
            var attachment = stored.stream().filter(message -> "user".equals(message.getRole()))
                    .findFirst().orElseThrow().getAttachments().get(0);
            assertEquals("Writer document", new String(files.read(2, attachment), StandardCharsets.UTF_8));
            assertThrows(com.zrlog.admin.business.ai.exception.AIFileInputException.class,
                    () -> files.read(1, attachment));
            assertTrue(conversations.captureAccount(1).exportAIMessage(-1L).getMessages().isEmpty());
            AdminTokenThreadLocal.remove();
            assertEquals("Writer document", new String(files.read(2, attachment), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void revocationWhileProviderIsRunningPreventsReplyAndConversationCommit() throws Exception {
        for (String mutation : List.of("enabled=false", "authVersion=1", "role='editor'")) {
            try (var db = database()) {
                var actor = actor(db);
                var started = new CountDownLatch(1);
                var proceed = new CountDownLatch(1);
                Model model = new Model(db, new AIFileService(), answer());
                model.afterRequest = () -> {
                    started.countDown();
                    try { assertTrue(proceed.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new RuntimeException(error); }
                };
                var response = model.start(actor, input(), audit(), null);
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    db.execute("update user set " + mutation + " where userId=2");
                } finally { proceed.countDown(); }
                String wire = read(response);
                assertTrue(wire, wire.contains("\"error\":\"permission\""));
                assertFalse(wire, wire.contains("PRIVATE ANSWER"));
                assertFalse(wire.contains("\"type\":\"done\""));
                assertTrue(AIServiceDependencies.conversations(db.dataSource()).captureAccount(2)
                        .exportAIMessage(-2L).getMessages().isEmpty());
                assertThrows(PermissionErrorException.class, () -> model.getRun(actor, 0));
            }
        }
    }

    @Test
    public void delegatedActorCannotAcquireWriteToolsOnWorker() throws Exception {
        try (var db = database()) {
            knowledge(db, "own_all");
            var actor = actor(db).restrictActions(Set.of("article.assist", "article.read"));
            var model = new Model(db, new AIFileService(), call("create_article",
                    "{\"title\":\"Forbidden\",\"typeId\":1,\"status\":\"draft\",\"content\":\"<p>Forbidden</p>\"}"), answer());
            AdminTokenThreadLocal.remove();
            String wire = read(model.start(actor, input(), audit(), null));
            assertTrue(wire, wire.contains("\"type\":\"done\""));
            assertFalse(model.requests.get(0).getAsJsonArray("tools").toString().contains("create_article"));
            var toolReply = model.requests.get(1).getAsJsonArray("messages").asList().stream()
                    .map(value -> value.getAsJsonObject()).filter(message -> "tool".equals(message.get("role").getAsString()))
                    .findFirst().orElseThrow();
            assertFalse(JsonParser.parseString(toolReply.get("content").getAsString()).getAsJsonObject()
                    .get("error").getAsString().isBlank());
            assertEquals(0, ((Number) db.scalar("select count(*) from log where userId=2")).intValue());
            assertNull(AdminTokenThreadLocal.getUser());
            assertFalse(DelegatedAccess.active());
        }
    }

    @Test
    public void pendingUploadAndCancelledPreviewRecheckEffectivePermissionsAndPreferences() throws Exception {
        try (var db = database()) {
            knowledge(db, "own_all");
            var actor = actor(db);
            var model = new Model(db, new AIFileService(), call("upload_attachment",
                    "{\"filename\":\"note.txt\",\"data\":\"aGVsbG8=\"}"));
            String wire = read(model.start(actor, input(), audit(), null));
            assertTrue(wire, wire.contains("approval-required"));
            assertNotNull(model.getRun(actor, 0).approval);
            var restricted = model.getRun(actor.restrictActions(Set.of("article.assist", "article.read")), 0);
            assertEquals("permission", restricted.error);
            assertNull(restricted.input);
            assertNull(restricted.approval);
            var store = AIServiceDependencies.approvals(db.dataSource());
            var run = store.read(2, 0); store.save(run, "cancelled");
            assertNotNull(model.getRun(actor, 0).input);
            knowledge(db, "off");
            var cancelled = model.getRun(actor, 0);
            assertEquals("permission", cancelled.error);
            assertNull(cancelled.input);
            assertNull(cancelled.approval);
        }
    }

    @Test
    public void synchronousSkillRechecksActorBeforePersistingProviderResult() throws Exception {
        try (var db = database()) {
            var actor = actor(db);
            var accounts = AccountServicesFixture.component(db.dataSource());
            var store = AIServiceDependencies.conversations(db.dataSource()).captureAccount(2);
            var tools = new AIToolService(AIServiceDependencies.config(db.dataSource())) {
                @Override public GenerateArticleTitleResponse generateArticleTitles(
                        GenerateArticleTitleRequest request, String context) {
                    try { db.execute("update user set authVersion=1 where userId=2"); }
                    catch (Exception error) { throw new RuntimeException(error); }
                    var result = new GenerateArticleTitleResponse(); result.setTitles(List.of("Generated title"));
                    return result;
                }
            };
            var skills = new AIWritingSkillService(actor, store, tools,
                    AIServiceDependencies.images(db.dataSource()), accounts.identities(), accounts.permissions());
            assertThrows(PermissionErrorException.class,
                    () -> skills.runToolResponse("title", -2L, "title", context()));
            assertTrue(store.exportAIMessage(-2L).getMessages().isEmpty());
        }
    }

    @Test
    public void automaticPublishCheckKeepsActorAndRejectsRevocationBeforeCommit() throws Exception {
        try (var db = database()) {
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy) values(20,2,1,'Publish',false,false)");
            var accounts = AccountServicesFixture.component(db.dataSource());
            AIWritingSkillService.Factory writing = (actor, store) ->
                    new AIWritingSkillService(actor, store, AIServiceDependencies.tools(db.dataSource()),
                            AIServiceDependencies.images(db.dataSource()), accounts.identities(), accounts.permissions()) {
                        @Override public List<AIResponseEntry.AIContentEntry> runToolResponseWithoutPersistence(
                                String input, Long id, String tool, GenerateArticleFieldRequest context) {
                            assertEquals(2, actor.getUserId());
                            assertNull(AdminTokenThreadLocal.getUser());
                            assertFalse(DelegatedAccess.active());
                            try { db.execute("update user set enabled=false where userId=2"); }
                            catch (Exception error) { throw new RuntimeException(error); }
                            return List.of(new AIResponseEntry.AIContentEntry("assistant", "PRIVATE CHECK"));
                        }
                    };
            var service = new AIPublishCheckService(ApplicationServicesFixture.settings(db.dataSource()),
                    ApplicationServicesFixture.operations(db.dataSource()), writing,
                    AIServiceDependencies.conversations(db.dataSource()), accounts.identities(), accounts.permissions());
            var detail = new AIArticleGlobalResponse(); var article = new LoadEditArticleResponse(); article.setLogId(20);
            detail.setArticle(article); detail.setPublishCheckEnabled(true); detail.setAiConfigured(true);
            var task = service.startPublishCheck(actor(db), detail, new CreateArticleRequest());
            var error = assertThrows(ExecutionException.class, () -> task.getFuture().get(5, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof PermissionErrorException);
            assertTrue(AIServiceDependencies.conversations(db.dataSource()).captureAccount(2)
                    .exportAIMessage(20L).getMessages().isEmpty());
        }
    }

    private InMemoryZrLogDatabase database() throws Exception {
        var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "gpt-4.1");
        db.putWebsite("ai_base_url", "https://provider.example/v1");
        db.execute("insert into user(userId,userName,role,preferences) values(2,'writer','author',?)",
                "{\"assistant\":{\"knowledgeScope\":\"off\"}}");
        return db;
    }
    private static void knowledge(InMemoryZrLogDatabase db, String value) throws Exception {
        db.execute("update user set preferences=? where userId=2", "{\"assistant\":{\"knowledgeScope\":\"" + value + "\"}}");
    }
    private static AccountAccess actor(InMemoryZrLogDatabase db) throws Exception {
        return AccountAccess.from(new User(db.dataSource()).loadById(2));
    }
    private static AdminAuditContext audit() { return new AdminAuditContext(2, "192.0.2.2", "/api/admin/article/ai", null); }
    private static ChatRequest input() { var input = new ChatRequest(); input.input = "Writer question"; return input; }
    private static GenerateArticleFieldRequest context() {
        var context = new GenerateArticleFieldRequest(); context.setTitle("Title"); context.setMarkdown("Article body"); return context;
    }
    private static String read(com.zrlog.admin.business.ai.dto.AIStreamResponse response) throws IOException {
        return new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    private static String answer() { return "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"PRIVATE ANSWER\"}}"; }
    private static String call(String name, String args) {
        return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\""
                + name + "\",\"arguments\":" + new Gson().toJson(args) + "}}]}}";
    }
    private static final class Model extends AIChatService {
        private final List<String> replies;
        final List<JsonObject> requests = new ArrayList<>();
        AccountAccess seenActor;
        Runnable afterRequest = () -> {};
        Model(InMemoryZrLogDatabase db, AIFileService files, String... replies) {
            super(null, ContentToolsFixture.from(db.config()),
                    AccountServicesFixture.component(db.dataSource()).permissions(),
                    AccountServicesFixture.component(db.dataSource()).identities(),
                    AccountServicesFixture.component(db.dataSource()).preferences(),
                    AIServiceDependencies.approvals(db.dataSource()), files,
                    AIServiceDependencies.writing(db.dataSource()), AIServiceDependencies.conversations(db.dataSource()),
                    AIServiceDependencies.writingTools(db.dataSource()),
                    AIServiceDependencies.references(ContentToolsFixture.from(db.config()), db.dataSource()));
            this.replies = List.of(replies);
        }
        @Override protected AIProviderResponses.Choice complete(AccountAccess actor, AIWebSiteInfo info,
                String body, AIChatStreamReader.Progress progress) throws IOException {
            assertNull(AdminTokenThreadLocal.getUser());
            assertFalse(DelegatedAccess.active());
            seenActor = actor; requests.add(JsonParser.parseString(body).getAsJsonObject());
            afterRequest.run();
            var result = new Gson().fromJson(replies.get(requests.size() - 1), AIProviderResponses.Choice.class);
            if (result.getMessage().getContent() != null) progress.emit("delta", result.getMessage().getContent());
            return result;
        }
    }
}
