package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.automation.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Real scheduler, DI graph, content tools and database; only the model provider is a local scripted server. */
@RunWith(Parameterized.class)
public class AIScheduledTaskTest {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> databases() {
        return Arrays.asList(new Object[]{"h2"}, new Object[]{"sqlite"}, new Object[]{"web-api"});
    }
    private final String backend;
    private final Gson json = new Gson();
    private final List<JsonObject> requests = new CopyOnWriteArrayList<>();
    private final Queue<String> replies = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
    private InMemoryZrLogDatabase db;
    private HttpServer provider;
    private AccountAccess actor;
    private AutomationComponent component;
    private CheckedAction onRequest = () -> { };

    public AIScheduledTaskTest(String backend) { this.backend = backend; }

    @Before public void open() throws Exception {
        db = backend.equals("sqlite") ? InMemoryZrLogDatabase.openSqlite()
                : backend.equals("web-api") ? InMemoryZrLogDatabase.openWebApi() : InMemoryZrLogDatabase.open();
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/chat/completions", exchange -> {
            requests.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            try { onRequest.run(); }
            catch (Throwable error) { handlerFailure.set(error); }
            String reply = replies.poll();
            byte[] body = Objects.toString(reply, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply == null ? 500 : 200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        provider.start();
        db.putWebsite("ai_provider", "DEEP_SEEK");
        db.putWebsite("ai_model", "scheduled-test");
        db.putWebsite("ai_base_url", "http://127.0.0.1:" + provider.getAddress().getPort());
        db.execute("insert into type(typeId,typeName,alias) values(?,?,?)", 301, "Writing", "writing-301");
        db.loadAdminWebModules();
        actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
        component = db.component(AutomationComponent.class);
    }

    @After public void close() throws Exception {
        if (provider != null) provider.stop(0);
        if (db != null) db.close();
        if (handlerFailure.get() != null) throw new AssertionError("Local model assertion failed", handlerFailure.get());
    }

    private StoredTask task(boolean allowDraft) throws Exception {
        var plan = new AutomationModels.Plan();
        plan.name = "Weekly writing plan";
        plan.type = AIScheduledTaskProvider.TYPE;
        plan.schedule = new AutomationModels.Schedule();
        plan.schedule.kind = "interval";
        plan.schedule.intervalMinutes = 10080;
        plan.schedule.at = System.currentTimeMillis() + 60000;
        plan.parameters.addProperty("goal", "Review my writing and prepare a useful draft when authorized.");
        plan.authorizedActions = allowDraft ? List.of("article.create") : List.of();
        return component.tasks().save(actor, plan);
    }

    private AutomationModels.Run run(StoredTask task) throws Exception {
        return component.engine().runNow(actor, task.id, task.revision, 130000);
    }

    @Test public void readOnlyGoalUsesExistingToolsAndReturnsAnOwnerNotificationWithoutDraftPermission() throws Exception {
        replies.add(tool("list_categories", new JsonObject()));
        replies.add(answer("Consider a weekly article about your writing progress."));
        var run = run(task(false));
        assertEquals("success", run.status);
        assertTrue(run.result.notifyOwner);
        assertEquals(1, run.result.children.size());
        assertEquals("success", run.result.children.get(0).status);
        assertTrue(run.result.text.contains("weekly article"));
        Set<String> offered = names(requests.get(0));
        assertTrue(offered.contains("search_articles"));
        assertFalse(offered.contains("create_article"));
        assertFalse(offered.contains("publish_article"));
        assertFalse(offered.contains("upload_attachment"));
        assertEquals(0, countDrafts());
        assertEquals(run.result.text, component.store().run(run.id).value.result.text);
    }

    @Test public void authorizedNewDraftUsesRealContentToolsAndCheckpointsBeforeTheFinalModelReply() throws Exception {
        replies.add(tool("list_categories", new JsonObject()));
        replies.add(tool("create_article", draft()));
        replies.add(answer("Your draft is saved and ready for review."));
        var task = task(true);
        onRequest = () -> {
            if (requests.size() == 3) {
                var active = component.store().task(task.id).value.activeRun;
                var durable = component.store().run(active.id).value;
                assertEquals("running", durable.status);
                assertEquals(2, durable.result.children.size());
                assertEquals("success", durable.result.children.get(1).status);
                assertNotNull(durable.result.children.get(1).actionPath);
                assertEquals(1, countDrafts());
            }
        };
        var run = run(task);
        assertEquals("success", run.status);
        assertEquals("admin.automation.ai.draftsSaved", run.result.messageKey);
        assertEquals(1, countDrafts());
        assertTrue(names(requests.get(0)).contains("create_article"));
        assertFalse(names(requests.get(0)).contains("update_article"));
        assertFalse(names(requests.get(0)).contains("publish_article"));
        JsonObject definition = requests.get(0).getAsJsonArray("tools").asList().stream().map(v -> v.getAsJsonObject().getAsJsonObject("function"))
                .filter(v -> v.get("name").getAsString().equals("create_article")).findFirst().orElseThrow();
        assertEquals("[\"draft\"]", definition.getAsJsonObject("parameters").getAsJsonObject("properties").getAsJsonObject("status").get("enum").toString());
        assertEquals(1, ((Number) db.scalar("select count(*) from log where title=? and rubbish=? and userId=?", "Scheduled draft", true, 1)).intValue());
    }

    @Test public void forgedUnapprovedWritePausesWithoutExecutingIt() throws Exception {
        replies.add(tool("create_article", draft()));
        var task = task(false);
        var run = run(task);
        assertEquals("needs_attention", run.status);
        assertFalse(component.store().task(task.id).value.enabled);
        assertEquals(0, countDrafts());
    }

    @Test public void draftPermissionCannotBeUsedToPublish() throws Exception {
        var published = draft(); published.addProperty("status", "published");
        replies.add(tool("create_article", published));
        var run = run(task(true));
        assertEquals("needs_attention", run.status);
        assertEquals(0, countDrafts());
        assertEquals(0, ((Number) db.scalar("select count(*) from log where title=?", "Scheduled draft")).intValue());
    }

    @Test public void anUnofferedToolInTheBatchPreventsEarlierWritesInThatBatch() throws Exception {
        var body = JsonParser.parseString(tool("create_article", draft())).getAsJsonObject();
        var call = new JsonObject(); call.addProperty("id", "forged-publish"); call.addProperty("type", "function");
        var function = new JsonObject(); function.addProperty("name", "publish_article"); function.addProperty("arguments", "{\"id\":1,\"version\":0}");
        call.add("function", function);
        body.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").getAsJsonArray("tool_calls").add(call);
        replies.add(body.toString());
        var run = run(task(true));
        assertEquals("needs_attention", run.status);
        assertEquals(0, countDrafts());
        assertTrue(run.result.children.isEmpty());
    }

    @Test public void modelFailureAfterSavingPreservesTheDraftAndPausesToPreventRepeatingPartialWork() throws Exception {
        replies.add(tool("create_article", draft()));
        var task = task(true);
        var run = run(task);
        assertEquals("needs_attention", run.status);
        assertEquals("admin.automation.ai.partial", run.result.messageKey);
        assertEquals(1, countDrafts());
        assertEquals("success", component.store().run(run.id).value.result.children.get(0).status);
        assertFalse(component.store().task(task.id).value.enabled);
        assertEquals(0, component.engine().tick("external", 130000).executed);
        assertEquals(1, countDrafts());
    }

    @Test public void identityRevokedDuringModelResponseCannotExecuteReturnedTools() throws Exception {
        replies.add(tool("create_article", draft()));
        onRequest = () -> db.execute("update user set enabled=? where userId=?", false, 1);
        var task = task(true);
        var run = run(task);
        assertEquals("needs_attention", run.status);
        assertEquals(0, countDrafts());
    }

    private int countDrafts() throws Exception {
        return ((Number) db.scalar("select count(*) from log where title=?", "Scheduled draft")).intValue();
    }

    private JsonObject draft() {
        var args = new JsonObject();
        args.addProperty("title", "Scheduled draft"); args.addProperty("typeId", 301); args.addProperty("status", "draft");
        args.addProperty("markdown", "# Draft\n\nWriting progress."); args.addProperty("content", "<h1>Draft</h1><p>Writing progress.</p>");
        return args;
    }

    private String tool(String name, JsonObject args) {
        return json.toJson(Map.of("choices", List.of(Map.of("finish_reason", "tool_calls", "message",
                Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "call-" + name, "type", "function",
                        "function", Map.of("name", name, "arguments", args.toString()))))))));
    }

    private String answer(String text) {
        return json.toJson(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("role", "assistant", "content", text)))));
    }

    private Set<String> names(JsonObject request) {
        Set<String> result = new HashSet<>();
        request.getAsJsonArray("tools").forEach(t -> result.add(t.getAsJsonObject().getAsJsonObject("function").get("name").getAsString()));
        return result;
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
