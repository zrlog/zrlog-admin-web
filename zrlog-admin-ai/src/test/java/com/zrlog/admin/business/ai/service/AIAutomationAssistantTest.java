package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.automation.AutomationAssistant;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AIAutomationAssistantTest {
    @Test
    public void configuredAssistantUsesExistingModelProtocolWithoutToolsOrTaskWrites() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            configure(db);
            String proposal = "{\"explanation\":\"Review this reminder.\",\"plans\":[{\"name\":\"Writing plan\","
                    + "\"type\":\"personal.reminder\",\"parameters\":{\"text\":\"Plan writing\"},"
                    + "\"schedule\":{\"kind\":\"weekly\",\"time\":\"17:00\",\"timezone\":\"Asia/Shanghai\",\"weekdays\":[5]}}]}";
            var client = client(proposal);
            var service = assistant(db, client);
            var actor = actor(db);
            assertTrue(service.available(actor));
            var definition = new AutomationModels.Definition(); definition.type = "personal.reminder";
            var result = service.propose(actor, request(), List.of(definition), 1793260800000L);
            assertEquals("Writing plan", result.plans.get(0).name);
            assertEquals(List.of(5), result.plans.get(0).schedule.weekdays);
            var body = client.requestBodies.get(0);
            assertFalse(body.has("tools"));
            assertFalse(body.toString().contains("test-provider-secret"));
            assertEquals("qwen3", body.get("model").getAsString());
            assertTrue(body.getAsJsonArray("messages").get(0).toString().contains("personal.reminder"));
            assertTrue(body.getAsJsonArray("messages").get(0).toString().contains("Asia/Shanghai"));
            assertEquals("0", db.scalar("select count(*) from website where name like 'assistant.%'").toString());
        }
    }

    @Test
    public void unconfiguredAiDoesNotPreventOrdinaryTaskUseOrContactAProvider() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            var client = client("{}");
            assertFalse(assistant(db, client).available(actor(db)));
            assertTrue(client.requests.isEmpty());
        }
    }

    @Test
    public void malformedModelOutputIsRejectedInsteadOfBecomingAPlan() throws Exception {
        try (var db = InMemoryZrLogDatabase.openWebApi()) {
            configure(db);
            assertThrows(AIResponseException.class, () -> assistant(db, client("I already published it"))
                    .propose(actor(db), request(), List.of(), 1793260800000L));
        }
    }

    private static AIChatServiceTest.FakeHttpClient client(String text) {
        var delta = new JsonObject(); delta.addProperty("content", text);
        var choice = new JsonObject(); choice.add("delta", delta); choice.addProperty("finish_reason", "stop");
        var body = new JsonObject(); body.add("choices", new Gson().toJsonTree(List.of(choice)));
        return new AIChatServiceTest.FakeHttpClient(AIChatServiceTest.streamResponse("data: " + body + "\n\ndata: [DONE]\n\n"));
    }

    private static void configure(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("ai_provider", "QWEN"); db.putWebsite("ai_model", "qwen3");
        db.putWebsite("ai_base_url", "http://localhost/test-model"); db.putWebsite("ai_api_key", "test-provider-secret");
    }

    private static AIAutomationAssistant assistant(InMemoryZrLogDatabase db, AIChatServiceTest.FakeHttpClient client) {
        var account = AccountServicesFixture.component(db.dataSource());
        return new AIAutomationAssistant(AIServiceDependencies.config(db.dataSource()), new AIChatModelClient(client, new AIFileService()),
                account.identities(), account.preferences());
    }

    private static AccountAccess actor(InMemoryZrLogDatabase db) throws Exception {
        return AccountAccess.from(new User(db.dataSource()).loadById(1));
    }

    private static AutomationAssistant.Request request() {
        var request = new AutomationAssistant.Request(); request.input = "Remind me to plan writing every Friday at 5pm";
        request.timezone = "Asia/Shanghai"; return request;
    }
}
