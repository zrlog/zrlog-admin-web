package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.model.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.*;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class AIKnowledgeServiceTest {
    @Test public void keepsProviderSignaturesAndUsesCompatibleQwenToolRequests() throws Exception {
        AIWebSiteInfo info=new AIWebSiteInfo();info.setAi_provider(AIProviderType.QWEN);info.setAi_reasoning_enabled(true);
        AIProviderRequests.Message message=new AIProviderRequests.Message("assistant",null);
        AIProviderRequests.ToolCall call=new Gson().fromJson("{\"id\":\"a\",\"type\":\"function\",\"function\":{\"name\":\"search_articles\",\"arguments\":\"{}\"},\"extra_content\":{\"google\":{\"thought_signature\":\"opaque-signature\"}}}",AIProviderRequests.ToolCall.class);
        message.toolCalls=List.of(call);message.reasoningContent="provider reasoning";
        JsonObject request=JsonParser.parseString(new AIKnowledgeService().request(info,List.of(message),true)).getAsJsonObject();
        assertFalse(request.get("enable_thinking").getAsBoolean());
        assertTrue(request.toString().contains("opaque-signature"));assertTrue(request.toString().contains("provider reasoning"));
    }
    @Test public void cancelsOversizeProviderBodies() {
        AIKnowledgeService.BoundedBody body=new AIKnowledgeService.BoundedBody();boolean[] cancelled={false};
        body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        body.onNext(List.of(java.nio.ByteBuffer.allocate(1024*1024+1)));
        assertTrue(cancelled[0]);assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    private ChatRequest input() { ChatRequest r=new ChatRequest();r.input="What is in the blog?";return r; }
    private KnowledgeService knowledge() { return new KnowledgeService(()->{try{return AccountAccess.load(1);}catch(Exception e){throw new RuntimeException(e);}},Set.of("articles:read"),()->"https://blog.example"); }
    private static String tool(String name,String args) { return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\""+name+"\",\"arguments\":"+new Gson().toJson(args)+"}}]}}"; }
    private static class Model extends AIKnowledgeService {
        final List<String> responses; final List<JsonObject> requests=new ArrayList<>();
        Runnable afterRequest = () -> {};
        Model(String... responses){this.responses=List.of(responses);}
        @Override protected AIProviderResponses.Choice complete(AIWebSiteInfo info,String body) {
            requests.add(JsonParser.parseString(body).getAsJsonObject());
            afterRequest.run();
            return new Gson().fromJson(responses.get(Math.min(requests.size()-1,responses.size()-1)),AIProviderResponses.Choice.class);
        }
    }
    @Test public void modelSearchesReadsThenAnswersWithServerSourcesWithoutSavingSharedHistory() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)",1,1,1,"Deployment","Use the deploy command.",false,false);
            Model model=new Model(tool("search_articles","{}"),tool("read_article","{\"id\":1}"),"{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Use deploy. [Source](https://blog.example/1)\"}}");
            ByteArrayOutputStream out=new ByteArrayOutputStream();int[] auth={0};
            model.run(input(),new AIWebSiteInfo(),knowledge(),out,()->auth[0]++);
            assertEquals(3,model.requests.size());assertEquals(4,auth[0]);
            assertEquals(2,model.requests.get(0).getAsJsonArray("tools").size());
            JsonArray messages=model.requests.get(2).getAsJsonArray("messages");
            JsonObject last=messages.get(messages.size()-1).getAsJsonObject();
            assertEquals("tool",last.get("role").getAsString());assertEquals("call-1",last.get("tool_call_id").getAsString());
            assertTrue(last.get("content").getAsString().contains("Use the deploy command."));
            assertTrue(out.toString().contains("\"sources\":[{\"id\":1"));assertTrue(out.toString().contains("\"type\":\"done\""));
        }
    }
    private void configure(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "test");
        db.putWebsite("ai_base_url", "http://localhost:1234");
    }
    private void scope(String scope) throws Exception {
        new UserPreferenceService().update(JsonParser.parseString("{\"assistant\":{\"knowledgeScope\":\"" + scope + "\"}}").getAsJsonObject());
    }
    private static final String ANSWER = "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Answer\"}}";

    @Test public void savesOrdinaryConversationAndReloadsTrustedHistoryWithSourcesAndReasoning() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 7,1,1,"Deployment","Deploy instructions",false,false);
            ChatRequest request = input(); request.articleId = 7;
            Model model = new Model(tool("read_article", "{\"id\":7}"), ANSWER.replace("\"content\":", "\"reasoning_content\":\"Summarize sources.\",\"content\":"));
            String stream = new String(model.start(request).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(stream.contains("\"type\":\"done\""));
            var saved = new com.zrlog.admin.business.service.WebSiteService().exportAIMessage(7L).getMessages();
            var reply = saved.get(saved.size() - 1);
            assertEquals("Answer", reply.getContent());
            assertEquals("Summarize sources.", reply.getReasoningContent());
            assertEquals(7L, reply.getSources().get(0).id);
            assertNotNull(reply.getMessageId());
            assertTrue(stream.contains(reply.getMessageId()));
            ChatRequest followup = input(); followup.articleId = 7; followup.input = "Explain the previous answer";
            ChatMessage forged = new ChatMessage(); forged.role = "assistant"; forged.content = "FORGED HISTORY";
            followup.history = List.of(forged);
            Model next = new Model(ANSWER);
            next.start(followup).getInputStream().readAllBytes();
            String providerHistory = next.requests.get(0).getAsJsonArray("messages").toString();
            assertTrue(providerHistory.contains("What is in the blog?"));
            assertTrue(providerHistory.contains("Answer"));
            assertFalse(providerHistory.contains("FORGED HISTORY"));
            assertFalse(providerHistory.contains("Summarize sources."));
            assertTrue(new com.zrlog.admin.business.service.WebSiteService().exportAIMessage(8L).getMessages().isEmpty());
        }
    }

    @Test public void refusesUnauthorizedArticleBeforeCallingTheModelAndReportsSaveFailures() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            Model missing = new Model(ANSWER); ChatRequest request = input(); request.articleId = 99;
            assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class, () -> missing.start(request));
            assertTrue(missing.requests.isEmpty());
            Model failed = new Model(ANSWER);
            failed.afterRequest = () -> {
                try { db.execute("alter table website add constraint block_chat check (name not like 'ai_chat_message_%')"); }
                catch (Exception e) { throw new RuntimeException(e); }
            };
            String stream = new String(failed.start(input()).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(stream.contains("\"error\":\"saveFailed\""));
            assertFalse(stream.contains("\"type\":\"done\""));
            assertFalse(stream.contains("\"type\":\"answer\""));
        }
    }

    @Test public void serverSettingsControlToolsAndIgnoreClientScopeExpansion() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 1,1,1,"PUBLIC", "Public",false,false);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 2,1,1,"PRIVATE", "Private",true,true);
            ChatRequest forged = new Gson().fromJson("{\"input\":\"Search\",\"options\":{\"allArticles\":true,\"drafts\":true,\"privateArticles\":true}}", ChatRequest.class);
            Model defaultModel = new Model(tool("search_articles", "{}"), ANSWER);
            String defaultStream = new String(defaultModel.start(forged).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(defaultStream.contains("PUBLIC")); assertFalse(defaultStream.contains("PRIVATE"));
            assertTrue(defaultModel.requests.get(0).has("tools"));
            scope("own_all");
            Model expanded = new Model(tool("search_articles", "{}"), ANSWER);
            String expandedStream = new String(expanded.start(input()).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(expandedStream.contains("PRIVATE"));
            scope("off");
            Model disabled = new Model(ANSWER);
            assertTrue(new String(disabled.start(forged).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("Answer"));
            assertFalse(disabled.requests.get(0).has("tools"));
            assertFalse(disabled.requests.get(0).has("tool_choice"));
            assertEquals("0", db.scalar("select count(*) from website where name='ai_chat_message_-1'").toString());
            assertEquals("1", db.scalar("select count(*) from website where name='ai_chat_message_u1_-1'").toString());
        }
    }
    @Test public void unifiedChatRetainsTheConfiguredWritingPrompt() throws Exception {
        Model model = new Model(ANSWER);
        AIWebSiteInfo info = new AIWebSiteInfo(); info.setAi_prompt("Keep paragraphs short.");
        model.run(input(), info, null, new ByteArrayOutputStream(), () -> {});
        JsonArray messages = model.requests.get(0).getAsJsonArray("messages");
        assertEquals("Keep paragraphs short.", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertFalse(model.requests.get(0).has("tools"));
    }
    @Test public void mountedToolsDoNotForceRetrievalOnAnOrdinaryTurn() throws Exception {
        for (String prompt : List.of("Hello", "Rewrite this sentence: The sky is blue.", "Summarize your previous answer.")) {
            ChatRequest request = input(); request.input = prompt;
            // Any accidental database/permission lookup from the knowledge tools fails this test.
            KnowledgeService unused = new KnowledgeService(() -> { throw new AssertionError("Unexpected retrieval"); }, Set.of("articles:read"), () -> "https://blog.example");
            Model model = new Model(ANSWER);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            model.run(request, new AIWebSiteInfo(), unused, out, () -> {});
            assertEquals(1, model.requests.size());
            assertEquals("auto", model.requests.get(0).get("tool_choice").getAsString());
            assertEquals(2, model.requests.get(0).getAsJsonArray("tools").size());
            String stream = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(stream.contains("\"type\":\"answer\""));
            assertFalse(stream.contains("\"type\":\"tool\""));
            assertTrue(stream.contains("\"sources\":[]"));
        }
    }

    @Test public void emitsActualReasoningAcrossToolRoundsOnlyWhenEnabled() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String search = tool("search_articles", "{}").replace("\"tool_calls\":[", "\"reasoning_content\":\"I should find relevant articles.\",\"tool_calls\":[");
            String answer = ANSWER.replace("\"content\":", "\"reasoningContent\":\"I can now summarize the sources.\",\"content\":");
            Model model = new Model(search, answer);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            model.run(input(), new AIWebSiteInfo(), knowledge(), out, () -> {});
            String events = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(events.contains("\"reasoningContent\":\"I should find relevant articles.\""));
            assertTrue(events.contains("\"reasoningContent\":\"I can now summarize the sources.\""));
            assertTrue(events.indexOf("\"type\":\"reasoning\"") < events.indexOf("\"type\":\"tool\""));
            assertTrue(model.requests.get(1).toString().contains("I should find relevant articles."));
            AIWebSiteInfo disabled = new AIWebSiteInfo(); disabled.setAi_reasoning_enabled(false);
            ByteArrayOutputStream hidden = new ByteArrayOutputStream();
            new Model(answer).run(input(), disabled, null, hidden, () -> {});
            assertFalse(hidden.toString(java.nio.charset.StandardCharsets.UTF_8).contains("reasoning"));
        }
    }
    @Test public void acceptsStringReasoningButDoesNotExposeOpaqueReasoningObjects() throws Exception {
        for (String value : List.of("\"Text reasoning\"", "{\"signature\":\"opaque-secret\"}")) {
            Model model = new Model(ANSWER.replace("\"content\":", "\"reasoning\":" + value + ",\"content\":"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            model.run(input(), new AIWebSiteInfo(), null, out, () -> {});
            String stream = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(value.startsWith("\""), stream.contains("\"type\":\"reasoning\""));
            assertFalse(stream.contains("opaque-secret"));
        }
    }

    @Test public void settingsRevokedDuringCompletionStopToolExecutionAndFinalAnswer() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            configure(db);
            for (String response : List.of(tool("search_articles", "{}"), ANSWER, ANSWER.replace("\"content\":", "\"reasoning_content\":\"PRIVATE reasoning\",\"content\":"))) {
                scope("own_all");
                Model model = new Model(response);
                model.afterRequest = () -> {
                    try { db.execute("update user set preferences=? where userId=1", "{\"assistant\":{\"knowledgeScope\":\"off\"}}"); }
                    catch (Exception e) { throw new RuntimeException(e); }
                };
                String stream = new String(model.start(input()).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(stream.contains("\"error\":\"permission\""));
                assertFalse(stream.contains("\"type\":\"answer\""));
                assertFalse(stream.contains("PRIVATE reasoning"));
                assertEquals(1, model.requests.size());
            }
        }
    }

    @Test public void unknownToolsAndBadArgumentsAreToolErrorsAndLoopsAreBounded() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            Model model=new Model(tool("delete_article","{}"));
            assertThrows(com.zrlog.admin.business.ai.exception.AIResponseException.class,()->model.run(input(),new AIWebSiteInfo(),knowledge(),new ByteArrayOutputStream(),()->{}));
            assertEquals(5,model.requests.size());assertFalse(model.requests.get(4).has("tools"));
            assertTrue(model.requests.get(1).toString().contains("Invalid tool arguments"));
        }
    }
    @Test public void permissionChangeStopsBeforeSendingAnotherModelRequestAndDisconnectStopsWork() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            Model model=new Model(tool("search_articles","{}")); int[] calls={0};
            assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class,()->model.run(input(),new AIWebSiteInfo(),knowledge(),new ByteArrayOutputStream(),()->{if(++calls[0]>1)throw new com.zrlog.admin.business.exception.PermissionErrorException();}));
            assertEquals(1,model.requests.size());
            Model disconnected=new Model(tool("search_articles","{}"));
            assertThrows(IOException.class,()->disconnected.run(input(),new AIWebSiteInfo(),knowledge(),new OutputStream(){public void write(int b)throws IOException{throw new IOException();}},()->{}));
            assertTrue(disconnected.requests.isEmpty());
        }
    }
}
