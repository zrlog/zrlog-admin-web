package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.ai.model.AIChatModels.ChatRequest;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.OAuthModels.Identity;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.security.AccountAction;
import com.zrlog.web.WebSetup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class AIChatToolParityTest {
    @Parameterized.Parameters(name = "database={0}")
    public static String[] databases() { return new String[]{"h2", "sqlite"}; }
    private final String database;
    public AIChatToolParityTest(String database) { this.database = database; }

    private InMemoryZrLogDatabase open() throws Exception {
        InMemoryZrLogDatabase db = database.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        db.putWebsite("ai_provider", "OPEN_AI");
        db.putWebsite("ai_model", "test");
        db.putWebsite("ai_base_url", "http://localhost:1234");
        db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\",\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
        return db;
    }

    @Test public void modelAndMcpExposeIdenticalNamesDescriptionsAndSchemasForEachRoleAndModuleState() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            for (String role : List.of("owner", "author", "contributor")) {
                db.execute("update user set role=? where userId=1", role);
                for (boolean enabled : List.of(false, true)) {
                    storage(enabled);
                    Model model = new Model(ANSWER);
                    assertTrue(model.chat().contains("\"type\":\"done\""));
                    JsonArray actual = model.requests.get(0).getAsJsonArray("tools");
                    Identity identity = identity();
                    McpContentService provider = new McpContentService(() -> identity, request(), "en_US");
                    JsonArray expected = new McpService("en_US").handle(
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", provider)
                            .body.getAsJsonObject("result").getAsJsonArray("tools");
                    assertEquals(8 + (enabled ? 1 : 0) - (role.equals("contributor") ? 1 : 0), actual.size());
                    assertEquals(expected.size(), actual.size());
                    for (int i = 0; i < expected.size(); i++) {
                        JsonObject tool = expected.get(i).getAsJsonObject();
                        JsonObject function = actual.get(i).getAsJsonObject().getAsJsonObject("function");
                        assertEquals(tool.get("name"), function.get("name"));
                        assertEquals(tool.get("description"), function.get("description"));
                        assertEquals(tool.get("inputSchema"), function.get("parameters"));
                    }
                }
            }
        }
    }

    @Test public void asynchronousModelCreatesReadsUpdatesAndPublishesUsingTheSharedWriteChain() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            var token = AdminTokenThreadLocal.getUser();
            String content = "<p>" + "Article body. ".repeat(500) + "</p>";
            JsonObject create = JsonParser.parseString("{\"title\":\"Original\",\"typeId\":1,\"status\":\"draft\"}").getAsJsonObject();
            create.addProperty("content", content);
            Model model = new Model(tool("create_article", create.toString()),
                    tool("get_article", "{\"id\":1}"),
                    tool("update_article", "{\"id\":1,\"version\":0,\"title\":\"Revised\"}"),
                    tool("publish_article", "{\"id\":1,\"version\":1}"), ANSWER);
            String wire = model.chat();
            assertTrue(wire, wire.contains("\"type\":\"done\""));
            assertTrue(wire.contains("\"tool\":\"create_article\""));
            assertTrue(wire.contains("\"tool\":\"publish_article\""));
            assertEquals(List.of(0, 1, 2), articleUpdates(wire).stream()
                    .map(event -> event.get("version").getAsInt()).collect(java.util.stream.Collectors.toList()));
            assertTrue(articleUpdates(wire).stream().allMatch(event -> event.get("articleId").getAsInt() == 1));
            assertTrue(wire.indexOf("\"type\":\"article-updated\"") < wire.indexOf("\"type\":\"answer\""));
            assertFalse(model.requests.get(4).has("tools"));
            Map<String, Object> row = db.queryOne("select * from log where logId=1");
            assertEquals("Revised", row.get("title"));
            assertEquals(content, row.get("content"));
            assertEquals(1, ((Number) row.get("userId")).intValue());
            assertEquals(2, ((Number) row.get("version")).intValue());
            assertFalse(com.zrlog.data.security.AccountAccess.truth(row.get("rubbish")));
            String auditJson = Objects.toString(db.scalar("select value from website where name='admin_audit_log'"));
            assertTrue(auditJson.contains("CREATE_ARTICLE"));
            assertTrue(auditJson.contains("UPDATE_ARTICLE"));
            var audit = new AdminAuditService().getRecentLogs();
            assertFalse(audit.isEmpty());
            assertTrue(audit.stream().allMatch(entry -> entry.getActorUserId() == 1));
            assertSame(token, AdminTokenThreadLocal.getUser());
        }
    }

    @Test public void conflictsAreReturnedToTheModelWithoutOverwritingTheArticle() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)", 1,1,1,"Current",true,false,2);
            Model model = new Model(tool("update_article", "{\"id\":1,\"version\":0,\"title\":\"Stale\"}"), ANSWER);
            String wire = model.chat();
            assertTrue(wire.contains("\"type\":\"done\""));
            assertTrue(articleUpdates(wire).isEmpty());
            assertTrue(model.requests.get(1).toString().contains("ADMIN_ARTICLE_UPDATE_EXPIRED"));
            assertEquals("Current", db.scalar("select title from log where logId=1"));
        }
    }

    @Test public void capturedDelegationHidesAndRejectsWritesOnTheWorker() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            Model model = new Model(tool("create_article", "{\"title\":\"Denied\",\"typeId\":1,\"status\":\"draft\"}"), ANSWER);
            String wire = DelegatedAccess.withPermissions(Set.of("article.assist", "article.read"), false, model::chat);
            assertTrue(wire.contains("\"type\":\"done\""));
            JsonArray definitions = model.requests.get(0).getAsJsonArray("tools");
            assertEquals(3, definitions.size());
            assertFalse(definitions.toString().contains("create_article"));
            assertTrue(model.requests.get(1).toString().contains("Invalid tool arguments"));
            assertEquals(0, ((Number) db.scalar("select count(*) from log")).intValue());
            assertFalse(DelegatedAccess.active());
        }
    }

    @Test public void savedUpdateIsEmittedEvenWhenTheNextModelTurnFails() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy,version) values(?,?,?,?,?,?,?)", 1,1,1,"Current",true,false,2);
            Model model = new Model(tool("get_article", "{\"id\":1}"),
                    tool("update_article", "{\"id\":1,\"version\":2,\"title\":\"Revised\"}"),
                    "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}");
            String wire = model.chat(1);
            assertEquals("Revised", db.scalar("select title from log where logId=1"));
            assertEquals(1, articleUpdates(wire).size());
            assertEquals(3, articleUpdates(wire).get(0).get("version").getAsInt());
            assertTrue(wire.contains("\"type\":\"error\""));
            assertFalse(wire.contains("\"type\":\"done\""));
            JsonObject context = model.requests.get(2).getAsJsonArray("messages").asList().stream()
                    .map(JsonElement::getAsJsonObject)
                    .filter(message -> message.has("content") && !message.get("content").isJsonNull()
                            && message.get("content").getAsString().startsWith("Current editor article metadata"))
                    .findFirst().orElseThrow();
            String content = context.get("content").getAsString();
            JsonObject metadata = JsonParser.parseString(content.substring(content.indexOf('\n') + 1)).getAsJsonObject();
            assertEquals(1, metadata.get("articleId").getAsInt());
            assertEquals(3, metadata.get("version").getAsInt());
            assertEquals("Revised", metadata.get("title").getAsString());
        }
    }

    private static List<JsonObject> articleUpdates(String wire) {
        return wire.lines().filter(line -> line.startsWith("data: "))
                .map(line -> JsonParser.parseString(line.substring(6)).getAsJsonObject())
                .filter(event -> event.get("type").getAsString().equals("article-updated")).collect(java.util.stream.Collectors.toList());
    }

    @Test public void revokingTheAccountAfterModelCompletionPreventsWrites() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            Model model = new Model(tool("create_article", "{\"title\":\"Denied\",\"typeId\":1,\"status\":\"draft\"}"));
            model.afterRequest = () -> {
                try { db.execute("update user set enabled=? where userId=1", false); }
                catch (Exception e) { throw new RuntimeException(e); }
            };
            String wire = model.chat();
            assertTrue(wire, wire.contains("\"error\":\"permission\""));
            assertEquals(0, ((Number) db.scalar("select count(*) from log")).intValue());
            assertEquals(1, model.requests.size());
        }
    }

    @Test public void uploadsAttachmentsThroughTheEnabledStorageWithTheAccountAndRequest() throws Exception {
        try (InMemoryZrLogDatabase db = open()) {
            storage(true);
            Model model = new Model(tool("upload_attachment", "{\"filename\":\"note.txt\",\"data\":\"aGVsbG8=\"}"), ANSWER);
            assertTrue(model.chat().contains("\"type\":\"done\""));
            assertTrue(model.requests.get(1).toString().contains("/attachment/note.txt"));
        }
    }

    private static Identity identity() {
        Identity identity = new Identity();
        identity.userId = 1;
        identity.authVersion = AdminTokenThreadLocal.getUser().getAuthVersion();
        identity.permissionMode = "inherit";
        identity.permissions = Arrays.stream(AccountAction.values()).map(AccountAction::getId).collect(java.util.stream.Collectors.toList());
        identity.scopes = new ArrayList<>(AccountPermissionService.current().scopes());
        return identity;
    }

    @SuppressWarnings("unchecked")
    private static void storage(boolean enabled) throws Exception {
        var field = ZrLogConfig.class.getDeclaredField("webSetups");
        field.setAccessible(true);
        List<WebSetup> setups = (List<WebSetup>) field.get(Constants.zrLogConfig);
        setups.removeIf(AttachmentStorage.class::isInstance);
        if (enabled) setups.add(new AttachmentStorage() {
            @Override public void setup() {}
            @Override public String saveAttachment(byte[] bytes, String filename, HttpRequest request) {
                assertEquals(1, AccountPermissionService.current().getUserId());
                assertEquals("/api/admin/article/ai", request.getUri());
                assertEquals("hello", new String(bytes, StandardCharsets.UTF_8));
                return "/attachment/" + filename;
            }
        });
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(AIChatToolParityTest.class.getClassLoader(), new Class[]{HttpRequest.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getUri": return "/api/admin/article/ai";
                case "getContextPath": return "";
                case "getRemoteHost": return "127.0.0.1";
                case "getHeaderMap": case "getParamMap": return Map.of();
                default: return method.getReturnType().isPrimitive() ? 0 : null;
            }
        });
    }

    private static final String ANSWER = "{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Done\"}}";
    private static String tool(String name, String arguments) {
        return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\""
                + name + "\",\"arguments\":" + new Gson().toJson(arguments) + "}}]}}";
    }

    private static class Model extends AIChatService {
        final List<String> responses;
        final List<JsonObject> requests = new ArrayList<>();
        Runnable afterRequest = () -> {};
        Model(String... responses) { super(AIChatToolParityTest.request()); this.responses = List.of(responses); }
        String chat() throws Exception {
            return chat(0);
        }
        String chat(long articleId) throws Exception {
            ChatRequest input = new ChatRequest(); input.input = "Save and publish the article as requested";
            input.articleId = articleId;
            String chunk = new String(start(input).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder wire = new StringBuilder(chunk);
            for (int i = 0; i < 8; i++) {
                var paused = chunk.lines().filter(line -> line.startsWith("data: "))
                        .map(line -> JsonParser.parseString(line.substring(6)).getAsJsonObject())
                        .filter(event -> event.get("type").getAsString().equals("approval-required")).findFirst();
                if (paused.isEmpty()) break;
                var view = paused.get().getAsJsonObject("run");
                var confirmation = new com.zrlog.admin.business.ai.model.AIChatModels.ApprovalRequest();
                confirmation.articleId = articleId; confirmation.runId = view.get("runId").getAsString();
                confirmation.approvalId = view.getAsJsonObject("approval").get("id").getAsString(); confirmation.decision = "approve";
                chunk = new String(resume(confirmation).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                wire.append(chunk);
            }
            return wire.toString();
        }
        @Override protected AIProviderResponses.Choice complete(AIWebSiteInfo info, String body, AIChatStreamReader.Progress progress) throws IOException {
            requests.add(JsonParser.parseString(body).getAsJsonObject());
            afterRequest.run();
            return new Gson().fromJson(responses.get(requests.size() - 1), AIProviderResponses.Choice.class);
        }
    }
}
