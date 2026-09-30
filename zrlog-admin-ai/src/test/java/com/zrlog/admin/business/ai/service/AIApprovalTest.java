package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AIApprovalTest {
    @Parameterized.Parameters(name="database={0}") public static String[] databases() { return new String[]{"h2", "sqlite", "webapi"}; }
    private final String database;
    public AIApprovalTest(String database) { this.database = database; }
    private InMemoryZrLogDatabase open() throws Exception {
        InMemoryZrLogDatabase db = database.equals("webapi") ? InMemoryZrLogDatabase.openWebApi()
                : database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "test"); db.putWebsite("ai_base_url", "http://localhost:1234");
        db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\",\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
        db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)", 7,1,1,"Before",true,false,2);
        return db;
    }
    private static final String ANSWER = "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Saved\"}}";
    private static String call(String name, String args) {
        return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"original-call\",\"type\":\"function\",\"extra_content\":{\"google\":{\"thought_signature\":\"signature\"}},\"function\":{\"name\":\""
                + name + "\",\"arguments\":" + new Gson().toJson(args) + "}}]}}";
    }
    private static class Model extends AIChatService {
        final List<String> replies;
        final List<JsonObject> requests = new ArrayList<>();
        Model(String... replies) { this.replies = List.of(replies); }
        String start() throws Exception {
            ChatRequest input = new ChatRequest(); input.input = "Update the title"; input.articleId = 7;
            return new String(start(input).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        String decide(ApprovalRequest input) throws Exception {
            return new String(resume(input).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        @Override protected AIProviderResponses.Choice complete(AIWebSiteInfo info, String body, AIChatStreamReader.Progress progress) throws IOException {
            requests.add(JsonParser.parseString(body).getAsJsonObject());
            if (requests.size() > replies.size()) throw new AssertionError("Unexpected model request");
            return new Gson().fromJson(replies.get(requests.size()-1), AIProviderResponses.Choice.class);
        }
    }
    private RunView pause(Model model) throws Exception {
        String wire = model.start();
        assertTrue(wire, wire.contains("\"type\":\"approval-required\""));
        assertFalse(wire.contains("\"type\":\"done\""));
        return new AIChatService().getRun(7);
    }
    private ApprovalRequest decision(RunView view, String decision) {
        ApprovalRequest request = new ApprovalRequest(); request.articleId = 7; request.runId = view.runId;
        request.approvalId = view.approval.id; request.decision = decision; return request;
    }
    private Model update() { return new Model(call("update_article", "{\"id\":7,\"version\":2,\"title\":\"After\"}")); }

    @Test public void resumesOnANewInstanceWithoutRepeatingTheModelCallAndReplaysDuplicateConfirmation() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            assertEquals("Before", db.scalar("select title from log where logId=7"));
            assertEquals("Before", view.approval.title);
            assertEquals("After", view.approval.changes.get(0).after);
            Model resumed = new Model(ANSWER);
            String result = resumed.decide(decision(view, "approve"));
            assertTrue(result, result.contains("\"type\":\"article-updated\""));
            assertTrue(result, result.contains("\"type\":\"done\""));
            assertEquals("After", db.scalar("select title from log where logId=7"));
            String request = resumed.requests.get(0).toString();
            assertTrue(request.contains("original-call")); assertTrue(request.contains("signature"));
            assertTrue(request.contains("\"tool_call_id\":\"original-call\""));
            assertEquals("completed", new AIChatService().getRun(7).status);
            assertTrue(new Model().decide(decision(view, "approve")).contains("completed"));
            assertEquals(3, ((Number)db.scalar("select version from log where logId=7")).intValue());
            assertEquals(2, new AIConversationService().exportAIMessage(7L).getMessages().stream()
                    .filter(message -> "knowledge".equals(message.getMessageType())).count());
        }
    }
    @Test public void rejectionIsAToolResultAndAnotherWriteRequiresAnotherApproval() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView first = pause(update());
            Model denied = new Model(ANSWER);
            assertTrue(denied.decide(decision(first, "reject")).contains("\"type\":\"done\""));
            assertTrue(denied.requests.get(0).toString().contains("user rejected"));
            assertEquals("Before", db.scalar("select title from log where logId=7"));
            RunView second = pause(update());
            Model continuation = new Model(call("update_article", "{\"id\":7,\"version\":3,\"digest\":\"Summary\"}"));
            assertTrue(continuation.decide(decision(second, "approve")).contains("approval-required"));
            RunView third = new AIChatService().getRun(7);
            assertEquals(second.runId, third.runId); assertNotEquals(second.approval.id, third.approval.id);
            assertTrue(new Model().decide(decision(second, "approve")).contains(third.approval.id));
            assertTrue(new Model(ANSWER).decide(decision(third, "approve")).contains("\"type\":\"done\""));
            assertEquals(4, ((Number)db.scalar("select version from log where logId=7")).intValue());
        }
    }
    @Test public void concurrentInstancesCanClaimOnlyOnce() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            pause(update());
            Run a = new AIApprovalStore().read(1,7), b = new AIApprovalStore().read(1,7);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (Run run : List.of(a,b)) results.add(pool.submit(() -> {
                    ready.countDown(); go.await();
                    try { new AIApprovalStore().save(run,"running"); return true; }
                    catch (AIApprovalStore.Changed e) { return false; }
                }));
                assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
                assertNotEquals(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
                assertTrue(new Model().decide(decision(new AIChatService().getRun(7), "approve")).contains("running"));
                assertEquals("Before", db.scalar("select title from log where logId=7"));
            } finally { pool.shutdownNow(); }
        }
    }
    @Test public void expiredWrongAccountAndWrongArticleCannotApprove() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            ApprovalRequest wrong = decision(view,"approve"); wrong.articleId = 0;
            assertThrows(PermissionErrorException.class, () -> new Model().decide(wrong));
            db.execute("insert into user(userId,userName,password,role,email) values(?,?,?,?,?)",2,"other","pass","owner","other@example.com");
            var token = AdminTokenThreadLocal.getUser();
            token.setUserId(2);
            try { assertThrows(PermissionErrorException.class, () -> new Model().decide(decision(view,"approve"))); }
            finally { token.setUserId(1); }
            AIApprovalStore store = new AIApprovalStore(); Run run = store.read(1,7);
            run.approval.expiresAt = 1; store.save(run,"awaiting_approval");
            assertTrue(new Model().decide(decision(view,"approve")).contains("expired"));
            assertEquals("Before", db.scalar("select title from log where logId=7"));
        }
    }
    @Test public void checksVersionAgainAndKeepsSavedResultsAfterModelFailure() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            db.execute("update log set title=?,version=3 where logId=7","Edited elsewhere");
            Model conflict = new Model(ANSWER);
            conflict.decide(decision(view,"approve"));
            assertTrue(conflict.requests.get(0).toString().contains("ADMIN_ARTICLE_UPDATE_EXPIRED"));
            assertEquals("Edited elsewhere", db.scalar("select title from log where logId=7"));
            RunView next = pause(new Model(call("update_article", "{\"id\":7,\"version\":3,\"title\":\"Saved before failure\"}")));
            Model failure = new Model("{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}");
            String wire = failure.decide(decision(next,"approve"));
            assertTrue(wire, wire.contains("article-updated")); assertTrue(wire.contains("\"status\":\"failed\""));
            assertEquals("Saved before failure", db.scalar("select title from log where logId=7"));
            assertTrue(new Model().decide(decision(next,"approve")).contains("failed"));
            assertEquals(4, ((Number)db.scalar("select version from log where logId=7")).intValue());
        }
    }
    @Test public void changedPermissionsAndInterruptedExecutionNeverReplayWrites() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            db.execute("update user set preferences=? where userId=1", "{\"assistant\":{\"knowledgeScope\":\"off\"}}");
            assertTrue(new Model().decide(decision(view,"approve")).contains("permission"));
            assertEquals("Before", db.scalar("select title from log where logId=7"));
            AIApprovalStore store = new AIApprovalStore(); Run run = store.read(1,7);
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\",\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
            run.error = null;
            store.save(run,"executing");
            assertTrue(new Model().decide(decision(view,"approve")).contains("executing"));
            run.updatedAt = 1;
            assertEquals("uncertain", AIApprovalStore.view(run).status);
        }
    }

    @Test public void hidesApprovalWhenTargetScopeOrModelChanges() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            db.execute("update log set userId=2 where logId=7");
            RunView hidden = new AIChatService().getRun(7);
            assertEquals("permission", hidden.error);
            assertNull(hidden.approval);
            String response = new Model().decide(decision(view, "approve"));
            assertFalse(response.contains("After"));
            assertEquals("Before", db.scalar("select title from log where logId=7"));
            db.execute("update log set userId=1 where logId=7");
            RunView next = pause(update());
            db.putWebsite("ai_model", "changed-model");
            assertNull(new AIChatService().getRun(7).approval);
            assertTrue(new Model().decide(decision(next, "approve")).contains("permission"));
        }
    }

    @Test public void doesNotPreviewWritesOutsideTheAssistantScope() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)", 8,2,1,"Other author secret",true,false,0);
            String response = new Model(call("update_article", "{\"id\":8,\"version\":0,\"title\":\"Changed\"}")).start();
            assertTrue(response.contains("permission"));
            assertFalse(response.contains("Other author secret"));
            assertNull(new AIChatService().getRun(7));
            assertEquals("Other author secret", db.scalar("select title from log where logId=8"));
        }
    }

    @Test public void clearingCancelsApprovalAndDoesNotRestoreCompletedMessages() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(update());
            assertTrue(new AIConversationService().clearAIMessage(7L));
            assertTrue(new Model().decide(decision(view, "approve")).contains("cancelled"));
            assertEquals("Before", db.scalar("select title from log where logId=7"));
            RunView next = pause(update());
            new Model(ANSWER).decide(decision(next, "approve"));
            assertTrue(new AIConversationService().clearAIMessage(7L));
            RunView cleared = new AIChatService().getRun(7);
            assertEquals("cancelled", cleared.status);
            assertNull(cleared.answer);
            assertTrue(new AIConversationService().exportAIMessage(7L).getMessages().isEmpty());
        }
    }

    @SuppressWarnings("unchecked")
    @Test public void unknownUploadOutcomeNeverRepeatsTheSideEffect() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            var field = com.zrlog.common.ZrLogConfig.class.getDeclaredField("webSetups");
            field.setAccessible(true);
            List<com.zrlog.web.WebSetup> setups = (List<com.zrlog.web.WebSetup>) field.get(com.zrlog.common.Constants.zrLogConfig);
            setups.removeIf(com.zrlog.admin.business.content.AttachmentStorage.class::isInstance);
            java.util.concurrent.atomic.AtomicInteger uploads = new java.util.concurrent.atomic.AtomicInteger();
            setups.add(new com.zrlog.admin.business.content.AttachmentStorage() {
                @Override public void setup() { }
                @Override public String saveAttachment(byte[] bytes, String filename, com.hibegin.http.server.api.HttpRequest request) {
                    uploads.incrementAndGet();
                    throw new IllegalArgumentException("Plugin failed after upload");
                }
            });
            RunView view = pause(new Model(call("upload_attachment", "{\"filename\":\"sample.txt\",\"data\":\"aGVsbG8=\"}")));
            assertFalse(new Gson().toJson(view).contains("aGVsbG8="));
            assertEquals("5", view.approval.changes.get(1).after);
            assertTrue(new Model().decide(decision(view, "approve")).contains("uncertain"));
            assertTrue(new Model().decide(decision(view, "approve")).contains("uncertain"));
            assertEquals(1, uploads.get());
        }
    }

    @Test public void concurrentConversationAppendsMergeAndDeduplicateAcrossInstances() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            List<AIConversationService> instances = List.of(new AIConversationService().captureAccount(), new AIConversationService().captureAccount());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            try {
                for (int worker = 0; worker < 2; worker++) {
                    final int index = worker;
                    results.add(pool.submit(() -> {
                        ready.countDown(); go.await();
                        for (int i = 0; i < 4; i++) {
                            var message = new com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry("assistant", index + ":" + i);
                            message.setMessageId(index + ":" + i);
                            instances.get(index).appendAIMessageEntries(List.of(message), 7L);
                            instances.get(index).appendAIMessageEntries(List.of(message), 7L);
                        }
                        return true;
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
                for (Future<Boolean> result : results) assertTrue(result.get(15, TimeUnit.SECONDS));
                var messages = new AIConversationService().exportAIMessage(7L).getMessages();
                assertEquals(8, messages.stream().filter(m -> "assistant".equals(m.getRole())).count());
                assertEquals(8, messages.stream().filter(m -> "assistant".equals(m.getRole())).map(m -> m.getMessageId()).distinct().count());
            } finally { pool.shutdownNow(); }
        }
    }
}
