package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.*;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.*;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.model.Log;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import com.zrlog.test.support.TestLogCapture;
import com.zrlog.util.I18nUtil;

import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.logging.Level;

public class AIChatConversationTest {

    private ZrLogConfig applicationConfig;

    @Test public void injectsAuthorizedArticleMetadataAndExcludesLegacySnapshotsAndBody() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            configure(db);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy,version) values(?,?,?,?,?,?,?,?)",
                    7,1,1,"Current title","BODY_NOT_AUTOMATICALLY_SENT",true,false,3);
            db.putWebsite("ai_chat_message_u1_7", "[{\"role\":\"user\",\"messageType\":\"articleContext\",\"content\":\"OBSOLETE_SNAPSHOT\"},"
                    + "{\"role\":\"user\",\"messageType\":\"knowledge\",\"content\":\"Earlier question\"}]");
            scope("off", db.dataSource());
            Model model = new Model(applicationConfig, ANSWER);
            ChatRequest request = input(); request.articleId = 7;
            model.start(request).getInputStream().readAllBytes();
            String providerMessages = model.requests.get(0).getAsJsonArray("messages").toString();
            assertFalse(providerMessages.contains("OBSOLETE_SNAPSHOT"));
            assertFalse(providerMessages.contains("BODY_NOT_AUTOMATICALLY_SENT"));
            assertTrue(providerMessages.contains("Earlier question"));
            assertFalse(model.requests.get(0).has("tools"));
            JsonObject metadata = currentArticle(model);
            assertEquals(7, metadata.get("articleId").getAsInt());
            assertEquals("Current title", metadata.get("title").getAsString());
            assertEquals("draft", metadata.get("status").getAsString());
            assertEquals(3, metadata.get("version").getAsInt());
            assertEquals(1, metadata.get("typeId").getAsInt());
            String stored = Objects.toString(db.scalar("select value from website where name='ai_chat_message_u1_7'"));
            assertTrue(stored.contains("OBSOLETE_SNAPSHOT"));
            assertFalse(stored.contains("Current editor article metadata"));
            Model draft = new Model(applicationConfig, ANSWER);
            draft.start(input()).getInputStream().readAllBytes();
            assertEquals("unsaved", currentArticle(draft).get("status").getAsString());
            assertEquals(0, currentArticle(draft).get("articleId").getAsInt());
            assertFalse(currentArticle(draft).has("version"));
        }
    }

    private JsonObject currentArticle(Model model) {
        return model.requests.get(0).getAsJsonArray("messages").asList().stream()
                .map(JsonElement::getAsJsonObject).map(message -> message.get("content").getAsString())
                .filter(content -> content.startsWith("Current editor article metadata"))
                .map(content -> JsonParser.parseString(content.substring(content.indexOf('\n') + 1)).getAsJsonObject())
                .findFirst().orElseThrow();
    }

    @Test public void asynchronousToolsFollowAccountLanguageWithoutLeakingTheBrowserLocale() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open();
                var ignored = AdminLanguageContext.open("zh_CN")) {
            configure(db);
            for (String language : List.of("en_US", "zh_CN", "en_US")) {
                db.execute("update user set preferences=? where userId=1", "{\"language\":\"" + language + "\"}");
                Model model = new Model(applicationConfig, tool("read_article", "{\"id\":999}"), ANSWER);
                model.start(input()).getInputStream().readAllBytes();
                JsonObject function = model.requests.get(0).getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("function");
                assertEquals("search_articles", function.get("name").getAsString());
                assertTrue(function.get("description").getAsString().startsWith(language.equals("en_US") ? "Search blog articles" : "按字面关键词"));
                String messages = model.requests.get(1).getAsJsonArray("messages").toString();
                assertTrue(messages, messages.contains(language.equals("en_US")
                        ? "Invalid tool arguments, unknown tool, or article unavailable" : "工具参数无效、工具不存在或文章不可用"));
                assertEquals("zh_CN", I18nUtil.getCurrentLocale());
            }
        }
    }
    @Test public void returnsDistinctSafeErrorsWithoutSavingFailedResponses() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open();
                var logs = TestLogCapture.forClass(AIChatService.class)) {
            configure(db);
            for (var failure :
                    Map.<Exception, String>of(
                                    new java.net.http.HttpTimeoutException(
                                                    "private provider detail"),
                                            "requestTimeout",
                                    new AIIncompleteResponseException("length"),
                                            "responseIncomplete",
                                    new AIRequestException("private provider detail"),
                                            "providerRequestFailed",
                                    new AIResponseException("private provider detail"),
                                            "providerResponseInvalid")
                            .entrySet()) {
                AIChatService service =
                        new AIChatService(
                                null,
                                ContentToolsFixture.from(applicationConfig),
                                AccountServicesFixture.component(db.dataSource()).permissions(),
                                AccountServicesFixture.component(db.dataSource()).preferences(),
                                AIServiceDependencies.approvals(db.dataSource()),
                                new AIFileService(),
                                AIServiceDependencies.writing(db.dataSource()),
                                AIServiceDependencies.conversations(db.dataSource()),
                                AIServiceDependencies.writingTools(db.dataSource()),
                                AIServiceDependencies.references(
                                        ContentToolsFixture.from(applicationConfig), db.dataSource())) {
                            @Override
                            protected AIProviderResponses.Choice complete(
                                    AIWebSiteInfo info,
                                    String body,
                                    AIChatStreamReader.Progress progress)
                                    throws IOException {
                                if (failure.getKey() instanceof IOException)
                                    throw (IOException) failure.getKey();
                                throw (RuntimeException) failure.getKey();
                            }
                        };
                String wire = new String(service.start(input()).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(wire.contains("\"error\":\"" + failure.getValue() + "\""));
                assertFalse(wire.contains("private provider detail"));
                assertFalse(wire.contains("\"type\":\"done\""));
                assertTrue(logs.contains(Level.WARNING, "error=" + failure.getValue()));
            }
            assertTrue(
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource()))
                            .exportAIMessage(-1L)
                            .getMessages()
                            .isEmpty());
        }
    }

    @Test public void keepsProviderSignaturesAndUsesCompatibleQwenToolRequests() throws Exception {
        AIWebSiteInfo info=new AIWebSiteInfo();info.setAi_provider(AIProviderType.QWEN);info.setAi_reasoning_enabled(true);
        AIProviderRequests.Message message=new AIProviderRequests.Message("assistant",null);
        AIProviderRequests.ToolCall call=new Gson().fromJson("{\"id\":\"a\",\"type\":\"function\",\"function\":{\"name\":\"search_articles\",\"arguments\":\"{}\"},\"extra_content\":{\"google\":{\"thought_signature\":\"opaque-signature\"}}}",AIProviderRequests.ToolCall.class);
        message.toolCalls=List.of(call);message.reasoningContent="provider reasoning";
        JsonObject request =
                JsonParser.parseString(
                                new AIChatService(
                                                AccountServicesFixture.component(null).permissions(),
                                                AccountServicesFixture.component(null).preferences(),
                                                AIServiceDependencies.approvals(null),
                                                new AIFileService(),
                                                AIServiceDependencies.writing(null),
                                                AIServiceDependencies.conversations(null),
                                                AIServiceDependencies.writingTools(null),
                                                AIServiceDependencies.references(null, null))
                                        .request(info, List.of(message), KnowledgeService.tools()))
                        .getAsJsonObject();
        assertTrue(request.get("enable_thinking").getAsBoolean());
        assertTrue(request.get("stream").getAsBoolean());
        assertTrue(request.toString().contains("opaque-signature"));assertTrue(request.toString().contains("provider reasoning"));
    }
    private ChatRequest input() { ChatRequest r=new ChatRequest();r.input="What is in the blog?";return r; }

    private KnowledgeService knowledge(DataSourceWrapper testDataSource) {
        return new KnowledgeService(
                () -> {
                    try {
                        return AccountAccess.from(new User(testDataSource).loadById(1));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                Set.of("articles:read"),
                () -> "https://blog.example",
                () -> false,
                () -> new Log(testDataSource));
    }

    private static String tool(String name,String args) { return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\""+name+"\",\"arguments\":"+new Gson().toJson(args)+"}}]}}"; }
    private static class Model extends AIChatService {
        final List<String> responses; final List<JsonObject> requests=new ArrayList<>();
        Runnable afterRequest = () -> {};

        Model(ZrLogConfig config, String... responses) {
            super(
                    null,
                    ContentToolsFixture.from(config),
                    AccountServicesFixture.component(config == null ? null : (DataSourceWrapper) config.getDataSource()).permissions(),
                    AccountServicesFixture.component(config == null ? null : (DataSourceWrapper) config.getDataSource()).preferences(),
                    AIServiceDependencies.approvals(config == null ? null : (DataSourceWrapper) config.getDataSource()),
                    new AIFileService(),
                    AIServiceDependencies.writing(config == null ? null : (DataSourceWrapper) config.getDataSource()),
                    AIServiceDependencies.conversations(config == null ? null : (DataSourceWrapper) config.getDataSource()),
                    AIServiceDependencies.writingTools(config == null ? null : (DataSourceWrapper) config.getDataSource()),
                    AIServiceDependencies.references(ContentToolsFixture.from(config), config == null ? null : (DataSourceWrapper) config.getDataSource()));
            this.responses = List.of(responses);
        }

        @Override protected AIProviderResponses.Choice complete(AIWebSiteInfo info,String body, AIChatStreamReader.Progress progress) throws IOException {
            requests.add(JsonParser.parseString(body).getAsJsonObject());
            afterRequest.run();
            AIProviderResponses.Choice choice = new Gson().fromJson(responses.get(Math.min(requests.size()-1,responses.size()-1)),AIProviderResponses.Choice.class);
            if (choice.getMessage().getReasoningText() != null) progress.emit("reasoning_delta", choice.getMessage().getReasoningText());
            if (choice.getMessage().getContent() != null) progress.emit("delta", choice.getMessage().getContent());
            return choice;
        }
    }
    @Test public void modelSearchesReadsThenAnswersWithServerSourcesWithoutSavingSharedHistory() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)",1,1,1,"Deployment","Use the deploy command.",false,false);
            Model model=new Model(applicationConfig, tool("search_articles","{}"),tool("read_article","{\"id\":1}"),"{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Use deploy. [Source](https://blog.example/1)\"}}");
            ByteArrayOutputStream out=new ByteArrayOutputStream();int[] auth={0};
            model.run(input(),new AIWebSiteInfo(),knowledge(db.dataSource()),out,()->auth[0]++);
            assertEquals(3,model.requests.size());assertTrue(auth[0]>=4);
            assertEquals(2,model.requests.get(0).getAsJsonArray("tools").size());
            JsonArray messages=model.requests.get(2).getAsJsonArray("messages");
            JsonObject last=messages.get(messages.size()-1).getAsJsonObject();
            assertEquals("tool",last.get("role").getAsString());assertEquals("call-1",last.get("tool_call_id").getAsString());
            assertTrue(last.get("content").getAsString().contains("Use the deploy command."));
            assertTrue(out.toString().contains("\"sources\":[{\"id\":1"));assertTrue(out.toString().contains("\"type\":\"done\""));
        }
    }
    private void configure(InMemoryZrLogDatabase db) throws Exception {
        applicationConfig = db.config();
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "test");
        db.putWebsite("ai_base_url", "http://localhost:1234");
    }
    private void scope(String scope, DataSourceWrapper testDataSource) throws Exception {
        new UserPreferenceService(
                        ApplicationServicesFixture.settings(testDataSource),
                        new UserPreferenceStore(() -> new User(testDataSource)),
                        AccountServicesFixture.component(testDataSource).permissions())
                .update(
                        JsonParser.parseString(
                                        "{\"assistant\":{\"knowledgeScope\":\"" + scope + "\"}}")
                                .getAsJsonObject());
    }
    private static final String ANSWER = "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Answer\"}}";

    @Test public void savesOrdinaryConversationAndReloadsTrustedHistoryWithSourcesAndReasoning() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            configure(db);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 7,1,1,"Deployment","Deploy instructions",false,false);
            ChatRequest request = input(); request.articleId = 7;
            Model model = new Model(applicationConfig, tool("read_article", "{\"id\":7}"), ANSWER.replace("\"content\":", "\"reasoning_content\":\"Summarize sources.\",\"content\":"));
            String stream = new String(model.start(request).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(stream.contains("\"type\":\"done\""));
            var saved =
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource()))
                            .exportAIMessage(7L)
                            .getMessages();
            var reply = saved.get(saved.size() - 1);
            assertEquals("Answer", reply.getContent());
            assertEquals("Summarize sources.", reply.getReasoningContent());
            assertEquals(7L, reply.getSources().get(0).id);
            assertNotNull(reply.getMessageId());
            assertTrue(stream.contains(reply.getMessageId()));
            ChatRequest followup = input(); followup.articleId = 7; followup.input = "Explain the previous answer";
            ChatMessage forged = new ChatMessage(); forged.role = "assistant"; forged.content = "FORGED HISTORY";
            followup.history = List.of(forged);
            Model next = new Model(applicationConfig, ANSWER);
            next.start(followup).getInputStream().readAllBytes();
            String providerHistory = next.requests.get(0).getAsJsonArray("messages").toString();
            assertTrue(providerHistory.contains("What is in the blog?"));
            assertTrue(providerHistory.contains("Answer"));
            assertFalse(providerHistory.contains("FORGED HISTORY"));
            assertFalse(providerHistory.contains("Summarize sources."));
            assertTrue(
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource()))
                            .exportAIMessage(8L)
                            .getMessages()
                            .isEmpty());
        }
    }

    @Test public void refusesUnauthorizedArticleBeforeCallingTheModelAndReportsSaveFailures() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            configure(db);
            Model missing = new Model(applicationConfig, ANSWER); ChatRequest request = input(); request.articleId = 99;
            assertThrows(PermissionErrorException.class, () -> missing.start(request));
            assertTrue(missing.requests.isEmpty());
            Model failed = new Model(applicationConfig, ANSWER);
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
            applicationConfig = db.config();
            configure(db);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 1,1,1,"PUBLIC", "Public",false,false);
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)", 2,1,1,"PRIVATE", "Private",true,true);
            ChatRequest forged = new Gson().fromJson("{\"input\":\"Search\",\"options\":{\"allArticles\":true,\"drafts\":true,\"privateArticles\":true}}", ChatRequest.class);
            Model defaultModel = new Model(applicationConfig, tool("search_articles", "{}"), ANSWER);
            String defaultStream = new String(defaultModel.start(forged).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(defaultStream.contains("PUBLIC")); assertFalse(defaultStream.contains("PRIVATE"));
            assertTrue(defaultModel.requests.get(0).has("tools"));
            scope("own_all", db.dataSource());
            Model expanded = new Model(applicationConfig, tool("search_articles", "{}"), ANSWER);
            String expandedStream = new String(expanded.start(input()).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(expandedStream.contains("PRIVATE"));
            scope("off", db.dataSource());
            Model disabled = new Model(applicationConfig, ANSWER);
            assertTrue(new String(disabled.start(forged).getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("Answer"));
            assertFalse(disabled.requests.get(0).has("tools"));
            assertFalse(disabled.requests.get(0).has("tool_choice"));
            assertEquals("0", db.scalar("select count(*) from website where name='ai_chat_message_-1'").toString());
            assertEquals("1", db.scalar("select count(*) from website where name='ai_chat_message_u1_-1'").toString());
        }
    }
    @Test public void unifiedChatRetainsTheConfiguredWritingPrompt() throws Exception {
        Model model = new Model(applicationConfig, ANSWER);
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
            KnowledgeService unused =
                    new KnowledgeService(
                            () -> {
                                throw new AssertionError("Unexpected retrieval");
                            },
                            Set.of("articles:read"),
                            () -> "https://blog.example",
                            () -> false,
                            () -> { throw new IllegalStateException("Database is unavailable in this test"); });
            Model model = new Model(applicationConfig, ANSWER);
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
            applicationConfig = db.config();
            String search = tool("search_articles", "{}").replace("\"tool_calls\":[", "\"reasoning_content\":\"I should find relevant articles.\",\"tool_calls\":[");
            String answer = ANSWER.replace("\"content\":", "\"reasoningContent\":\"I can now summarize the sources.\",\"content\":");
            Model model = new Model(applicationConfig, search, answer);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            model.run(input(), new AIWebSiteInfo(), knowledge(db.dataSource()), out, () -> {});
            String events = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(events.contains("\"reasoningContent\":\"I should find relevant articles.\""));
            assertTrue(events.contains("\"reasoningContent\":\"I can now summarize the sources.\""));
            assertTrue(events.indexOf("\"type\":\"reasoning\"") < events.indexOf("\"type\":\"tool\""));
            assertTrue(model.requests.get(1).toString().contains("I should find relevant articles."));
            AIWebSiteInfo disabled = new AIWebSiteInfo(); disabled.setAi_reasoning_enabled(false);
            ByteArrayOutputStream hidden = new ByteArrayOutputStream();
            new Model(applicationConfig, answer).run(input(), disabled, null, hidden, () -> {});
            assertFalse(hidden.toString(java.nio.charset.StandardCharsets.UTF_8).contains("reasoning"));
        }
    }
    @Test public void acceptsStringReasoningButDoesNotExposeOpaqueReasoningObjects() throws Exception {
        for (String value : List.of("\"Text reasoning\"", "{\"signature\":\"opaque-secret\"}")) {
            Model model = new Model(applicationConfig, ANSWER.replace("\"content\":", "\"reasoning\":" + value + ",\"content\":"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            model.run(input(), new AIWebSiteInfo(), null, out, () -> {});
            String stream = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(value.startsWith("\""), stream.contains("\"type\":\"reasoning\""));
            assertFalse(stream.contains("opaque-secret"));
        }
    }

    @Test public void settingsRevokedDuringCompletionStopToolExecutionAndFinalAnswer() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            configure(db);
            for (String response : List.of(tool("search_articles", "{}"), ANSWER, ANSWER.replace("\"content\":", "\"reasoning_content\":\"PRIVATE reasoning\",\"content\":"))) {
                scope("own_all", db.dataSource());
                Model model = new Model(applicationConfig, response);
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
            applicationConfig = db.config();
            Model model=new Model(applicationConfig, tool("delete_article","{}"));
            assertThrows(
                    AIResponseException.class,
                    () ->
                            model.run(
                                    input(),
                                    new AIWebSiteInfo(),
                                    knowledge(db.dataSource()),
                                    new ByteArrayOutputStream(),
                                    () -> {}));
            assertEquals(5,model.requests.size());assertFalse(model.requests.get(4).has("tools"));
            assertTrue(
                    model.requests
                            .get(1)
                            .toString()
                            .contains(
                                    I18nUtil.getAdminBackendStringFromRes(
                                            "admin.ai.error.knowledgeTool")));
        }
    }
    @Test public void permissionChangeStopsBeforeSendingAnotherModelRequestAndDisconnectStopsWork() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            Model model=new Model(applicationConfig, tool("search_articles","{}")); boolean[] revoked={false};
            model.afterRequest = () -> revoked[0] = true;
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            model.run(
                                    input(),
                                    new AIWebSiteInfo(),
                                    knowledge(db.dataSource()),
                                    new ByteArrayOutputStream(),
                                    () -> {
                                        if (revoked[0]) throw new PermissionErrorException();
                                    }));
            assertEquals(1,model.requests.size());
            Model disconnected=new Model(applicationConfig, tool("search_articles","{}"));
            assertThrows(IOException.class,()->disconnected.run(input(),new AIWebSiteInfo(),knowledge(db.dataSource()),new OutputStream(){public void write(int b)throws IOException{throw new IOException();}},()->{}));
            assertTrue(disconnected.requests.isEmpty());
        }
    }
}
