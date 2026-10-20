package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AiNativeImageUtils;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.WebSite;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RunWith(Parameterized.class)
public class AIWritingToolsTest {
    @Parameterized.Parameters(name="database={0}") public static String[] databases() { return new String[]{"h2", "sqlite", "webapi"}; }
    private final String database;
    public AIWritingToolsTest(String database) { this.database = database; }
    private static final Gson JSON = new Gson();
    private static final String REVISION = "0123456789abcdef0123456789abcdef";
    private static final String ANSWER = "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Here are the candidates; nothing has been saved.\"}}";
    private InMemoryZrLogDatabase open() throws Exception {
        InMemoryZrLogDatabase db = database.equals("webapi") ? InMemoryZrLogDatabase.openWebApi()
                : database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        db.putWebsite("ai_provider", "OPEN_AI"); db.putWebsite("ai_model", "test"); db.putWebsite("ai_base_url", "http://localhost:1234");
        db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\",\"assistant\":{\"knowledgeScope\":\"off\"}}");
        db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)",7,1,1,"Stored title",true,false,2);
        return db;
    }
    private static ChatRequest input() {
        ChatRequest request = new ChatRequest(); request.articleId = 7;
        request.input = "Generate titles, let me choose, then generate a digest";
        request.contextRevision = REVISION; request.editorContext = new GenerateArticleFieldRequest();
        request.editorContext.setTitle("Unsaved title"); request.editorContext.setMarkdown("Unsaved article body");
        return request;
    }
    private static String call(String name, String args) {
        return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-" + name
                + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":" + JSON.toJson(args) + "}}]}}";
    }
    private static class Model extends AIChatService {
        private final com.hibegin.common.dao.DataSourceWrapper actorSource;
        private final DataSourceWrapper testDataSource;
        final List<String> replies;
        final List<JsonObject> requests = new ArrayList<>();
        final List<String> generated = new ArrayList<>();
        String invalidPayload;

        Model(DataSourceWrapper testDataSource, String... replies) {
            super(
                    AccountServicesFixture.component(testDataSource).permissions(), AccountServicesFixture.component(testDataSource).identities(),
                    AccountServicesFixture.component(testDataSource).preferences(),
                    AIServiceDependencies.approvals(testDataSource),
                    new AIFileService(),
                    AIServiceDependencies.writing(testDataSource),
                    AIServiceDependencies.conversations(testDataSource),
                    AIServiceDependencies.writingTools(testDataSource),
                    AIServiceDependencies.references(null, testDataSource));
            this.actorSource = testDataSource;
            this.testDataSource = testDataSource;
            this.replies = List.of(replies);
        }

        String begin(ChatRequest request) throws Exception { return new String(start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(actorSource), request, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(actorSource), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()).getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
        String submit(InputRequest request) throws Exception { return new String(resumeInput(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(actorSource), request, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(actorSource), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()).getInputStream().readAllBytes(), StandardCharsets.UTF_8); }
        @Override protected AIWritingSkillService writingSkillService(com.zrlog.data.security.AccountAccess actor) {
            return new AIWritingSkillService(actor,
                    AIServiceDependencies.conversations(testDataSource).captureAccount(actor.getUserId()),
                    AIServiceDependencies.tools(testDataSource),
                    AIServiceDependencies.images(testDataSource),
                        com.zrlog.admin.support.AccountServicesFixture.component(testDataSource).identities(),
                        com.zrlog.admin.support.AccountServicesFixture.component(testDataSource).permissions()) {
                @Override
                AIContentEntry generate(
                        String key, GenerateArticleFieldRequest context, String instruction) {
                    assertEquals("Unsaved article body", context.getMarkdown());
                    generated.add(key + ":" + instruction);
                    AIContentEntry result = new AIContentEntry("assistant", "Validated " + key);
                    result.setTool(key);
                    result.setPayload(
                            JsonParser.parseString(
                                    invalidPayload != null
                                            ? invalidPayload
                                            : key.equals("title")
                                                    ? "{\"titles\":[\"First title\",\"Second title\"]}"
                                                    : "{\"digest\":\"Summary follows the chosen title\"}"));
                    return result;
                }
            };
        }
        @Override protected AIProviderResponses.Choice complete(com.zrlog.data.security.AccountAccess actor, AIWebSiteInfo info, String body, AIChatStreamReader.Progress progress) {
            JsonObject request = JsonParser.parseString(body).getAsJsonObject(); requests.add(request);
            if (requests.size() > replies.size()) throw new AssertionError("Unexpected model turn");
            String reply = replies.get(requests.size()-1);
            if (reply.contains("RESULT_ID")) {
                String resultId = "";
                for (JsonElement item : request.getAsJsonArray("messages")) {
                    JsonObject message = item.getAsJsonObject();
                    if (!"tool".equals(message.get("role").getAsString())) continue;
                    JsonObject output = JsonParser.parseString(message.get("content").getAsString()).getAsJsonObject();
                    if (output.has("resultId")) resultId = output.get("resultId").getAsString();
                }
                assertFalse(resultId.isEmpty()); reply = reply.replace("RESULT_ID", resultId);
            }
            return JSON.fromJson(reply, AIProviderResponses.Choice.class);
        }
    }
    private RunView pause(Model model, DataSourceWrapper testDataSource) throws Exception {
        String wire = model.begin(input());
        assertTrue(wire, wire.contains("interaction-required"));
        assertFalse(wire, wire.contains("\"type\":\"done\""));
        return new AIChatService(AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).identities(),
                        AccountServicesFixture.component(testDataSource).preferences(),
                        AIServiceDependencies.approvals(testDataSource),
                        new AIFileService(),
                        AIServiceDependencies.writing(testDataSource),
                        AIServiceDependencies.conversations(testDataSource),
                        AIServiceDependencies.writingTools(testDataSource),
                        AIServiceDependencies.references(null, testDataSource))
                .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(testDataSource), 7);
    }
    private static Model titles(DataSourceWrapper testDataSource) { return new Model(testDataSource, call("writing_title", "{\"instruction\":\"Two titles\"}"), call("request_writing_input", "{\"question\":\"Which title?\",\"resultId\":\"RESULT_ID\"}")); }
    private static InputRequest response(RunView view, String value) {
        InputRequest request = new InputRequest(); request.articleId = view.articleId; request.runId = view.runId;
        request.interactionId = view.interaction.id; request.decision = "submit"; request.value = value; request.contextRevision = REVISION;
        return request;
    }
    @Test public void selectsPersistedTitlesAndContinuesDigestOnANewInstanceWithoutRegeneration() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            Model first = titles(db.dataSource()); RunView view = pause(first, db.dataSource());
            assertEquals("awaiting_input", view.status);
            assertEquals(List.of("First title", "Second title"), view.interaction.options);
            assertEquals(1, view.skillMessages.size());
            assertEquals(List.of("title"), view.skillMessages.get(0).getSkillContract().applicableFields);
            String tools = first.requests.get(0).get("tools").toString();
            assertTrue(tools.contains("writing_title")); assertFalse(tools.contains("search_articles"));
            JsonObject titleTool = first.requests.get(0).getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("function");
            assertEquals(AIWritingSkillCatalog.instructions("title"), titleTool.get("description").getAsString());
            assertTrue(first.requests.get(0).toString().contains("Unsaved article body"));
            Model next = new Model(db.dataSource(), call("writing_digest", "{\"instruction\":\"Use the selected title\"}"), ANSWER);
            assertTrue(next.submit(response(view, "Second title")).contains("\"type\":\"done\""));
            assertEquals(1, first.generated.size()); assertEquals(1, next.generated.size());
            assertTrue(next.generated.get(0).contains("Second title"));
            assertTrue(next.requests.get(0).toString().contains("call-request_writing_input"));
            assertEquals("Stored title", db.scalar("select title from log where logId=7"));
            var stored =
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                            .exportAIMessage(7L)
                            .getMessages();
            assertEquals(2, stored.stream().filter(m -> m.getSkillContract() != null).count());
            assertTrue(stored.stream().anyMatch(m -> "writingInput".equals(m.getMessageType()) && "Second title".equals(m.getContent())));
            AIContentEntry selected = stored.stream().filter(m -> "title".equals(m.getTool())).findFirst().orElseThrow();
            assertEquals("Second title", JSON.toJsonTree(selected.getPayload()).getAsJsonObject().get("selectedTitle").getAsString());
            assertTrue(new Model(db.dataSource()).submit(response(view, "Second title")).contains("completed"));
            assertEquals(
                    stored.size(),
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                            .exportAIMessage(7L)
                            .getMessages()
                            .size());
        }
    }
    @Test public void rejectsForgedChoicesWrongOwnershipAndChangedDraftWithoutConsumingPendingInput() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(titles(db.dataSource()), db.dataSource());
            assertThrows(ArgsException.class, () -> new Model(db.dataSource()).submit(response(view, "Invented title")));
            assertEquals(
                    "awaiting_input",
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()))
                            .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7)
                            .status);
            InputRequest wrong = response(view, "First title"); wrong.articleId = 0;
            assertThrows(PermissionErrorException.class, () -> new Model(db.dataSource()).submit(wrong));
            db.execute("insert into user(userId,userName,password,role,email) values(?,?,?,?,?)",2,"other","pass","owner","other@example.com");
            var token = AdminTokenThreadLocal.getUser(); token.setUserId(2);
            try { assertThrows(PermissionErrorException.class, () -> new Model(db.dataSource()).submit(response(view, "First title"))); }
            finally { token.setUserId(1); }
            InputRequest changed = response(view, "First title"); changed.contextRevision = "abcdef0123456789abcdef0123456789";
            assertTrue(new Model(db.dataSource()).submit(changed).contains("contextChanged"));
            assertEquals(
                    "failed",
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()))
                            .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7)
                            .status);
        }
    }
    @Test public void textInputCancellationExpiryPermissionAndClearingAreDurable() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            RunView view = pause(new Model(db.dataSource(), call("request_writing_input", "{\"question\":\"Who is your audience?\"}")), db.dataSource());
            assertEquals("text", view.interaction.kind);
            Model next = new Model(db.dataSource(), ANSWER); assertTrue(next.submit(response(view, "Beginners")).contains("done"));
            assertTrue(next.requests.get(0).toString().contains("Beginners"));
            view = pause(titles(db.dataSource()), db.dataSource()); InputRequest cancel = response(view, null); cancel.decision = "cancel";
            Model cancelled = new Model(db.dataSource(), ANSWER); cancelled.submit(cancel);
            assertTrue(cancelled.requests.get(0).toString().contains("cancelled"));
            view = pause(titles(db.dataSource()), db.dataSource());
            Run run = new AIApprovalStore(AccountServicesFixture.component(db.dataSource()).security()).read(1, 7);
            run.interaction.expiresAt = 1;
            new AIApprovalStore(AccountServicesFixture.component(db.dataSource()).security())
                    .save(run, "awaiting_input");
            assertTrue(new Model(db.dataSource()).submit(response(view, "First title")).contains("expired"));
            view = pause(titles(db.dataSource()), db.dataSource()); db.putWebsite("ai_model", "different-model");
            RunView hidden =
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()))
                            .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7);
            assertNull(hidden.interaction);
            assertTrue(hidden.skillMessages.isEmpty());
            assertTrue(new Model(db.dataSource()).submit(response(view, "First title")).contains("permission"));
            db.putWebsite("ai_model", "test"); view = pause(titles(db.dataSource()), db.dataSource());
            assertTrue(
                    new AIConversationService(
                                    AccountServicesFixture.component(db.dataSource()).security(),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource()),
                                    new AIFileService(),
                                    AIServiceDependencies.approvals(db.dataSource())).captureAccount(Math.max(1, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUserId()))
                            .clearAIMessage(7L));
            assertTrue(new Model(db.dataSource()).submit(response(view, "First title")).contains("cancelled"));
            assertTrue(
                    new AIChatService(AccountServicesFixture.component(db.dataSource()).permissions(),
                        AccountServicesFixture.component(db.dataSource()).identities(),
                        AccountServicesFixture.component(db.dataSource()).preferences(),
                        AIServiceDependencies.approvals(db.dataSource()),
                        new AIFileService(),
                        AIServiceDependencies.writing(db.dataSource()),
                        AIServiceDependencies.conversations(db.dataSource()),
                        AIServiceDependencies.writingTools(db.dataSource()),
                        AIServiceDependencies.references(null, db.dataSource()))
                            .getRun(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), 7)
                            .skillMessages
                            .isEmpty());
        }
    }
    @Test public void replansCallsBatchedAfterAQuestionRatherThanExecutingGuessedActions() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            JsonObject batch = JsonParser.parseString(call("request_writing_input", "{\"question\":\"Who is your audience?\"}")).getAsJsonObject();
            JsonObject later = JsonParser.parseString(call("writing_digest", "{\"instruction\":\"Guess the audience\"}")).getAsJsonObject();
            batch.getAsJsonObject("message").getAsJsonArray("tool_calls").add(later.getAsJsonObject("message").getAsJsonArray("tool_calls").get(0));
            RunView view = pause(new Model(db.dataSource(), batch.toString()), db.dataSource());
            InputRequest cancel = response(view, null); cancel.decision = "cancel";
            Model resumed = new Model(db.dataSource(), ANSWER); resumed.submit(cancel);
            assertTrue(resumed.generated.isEmpty());
            assertTrue(resumed.requests.get(0).toString().contains("call-writing_digest"));
            assertTrue(resumed.requests.get(0).toString().contains("Not executed: user input was required"));
        }
    }
    @Test public void invalidResultsCannotProduceActionableCardsAndOldClientsDoNotExposeWritingTools() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            Model invalid = new Model(db.dataSource(), call("writing_title", "{\"instruction\":\"Titles\"}"), ANSWER); invalid.invalidPayload = "{\"titles\":[]}";
            String wire = invalid.begin(input()); assertFalse(wire, wire.contains("skill-result"));
            assertFalse(wire, wire.contains("skillContract"));
            Model legacy = new Model(db.dataSource(), ANSWER); ChatRequest request = input(); request.editorContext = null; request.contextRevision = null;
            legacy.begin(request); assertFalse(legacy.requests.get(0).has("tools"));
            Model forged = new Model(db.dataSource(), call("request_writing_input", "{\"question\":\"Choose\",\"resultId\":\"other-run\"}"), ANSWER);
            assertFalse(forged.begin(input()).contains("interaction-required"));
        }
    }
    @Test public void catalogRegistersAllMarkdownAndRejectsUnsafeOrUnusablePayloads() {
        for (String key : AIWritingSkillCatalog.KEYS) {
            assertTrue(AIWritingSkillCatalog.instructions(key).contains("name: " + key));
            assertTrue(
                    AiNativeImageUtils.resources().contains(AIWritingSkillCatalog.resource(key)));
        }
        for (String payload : List.of("{}", "{\"titles\":[]}", "{\"titles\":[42]}", "{\"titles\":[\"  \"]}"))
            assertThrows(AIResponseException.class, () -> AIWritingSkillCatalog.validateResult("title", JsonParser.parseString(payload)));
        for (String url : List.of("javascript:alert(1)", "//unknown.invalid/a.png", "data:text/html;base64,abcd")) {
            JsonObject payload = new JsonObject(); payload.addProperty("url", url);
            assertThrows(AIResponseException.class, () -> AIWritingSkillCatalog.validateResult("cover", payload));
        }
        ChatRequest request = input(); request.editorContext.setMarkdown("x".repeat(180001));
        assertThrows(ArgsException.class, request::doValid);
    }
}
