package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.automation.AutomationAssistant;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AutomationProposalAssemblyTest {
    @Test(timeout = 30000)
    public void productionGraphUsesProviderOutputOnlyAsAnUnsavedUnprivilegedPlan() throws Exception {
        AtomicReference<JsonObject> outbound = new AtomicReference<>();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/chat/completions", exchange -> {
            outbound.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            String content = "{\"explanation\":\"Review this plan before saving.\",\"plans\":[{\"id\":\"forged-id\",\"enabled\":true,"
                    + "\"executionActions\":[\"system.manage\"],\"name\":\"Write weekly\",\"type\":\"assistant.reminder\","
                    + "\"schedule\":{\"kind\":\"weekly\",\"timezone\":\"Asia/Shanghai\",\"time\":\"17:00\",\"weekdays\":[5]},"
                    + "\"parameters\":{\"text\":\"Plan next week's writing\",\"hiddenCommand\":\"publish\"}}]}";
            JsonObject delta = new JsonObject(); delta.addProperty("content", content);
            JsonObject choice = new JsonObject(); choice.add("delta", delta); choice.addProperty("finish_reason", "stop");
            byte[] body = ("data: {\"choices\":[" + choice + "]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        provider.start();
        try (var db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_provider", "DEEP_SEEK"); db.putWebsite("ai_model", "test-model");
            db.putWebsite("ai_base_url", "http://127.0.0.1:" + provider.getAddress().getPort());
            db.loadAdminWebModules();
            var component = db.component(AutomationComponent.class);
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(component.proposals().available(actor));
            long before = component.store().taskCount(actor.getUserId());
            var input = new AutomationAssistant.Request(); input.input = "Remind me to plan weekly writing on Friday at 5pm";
            input.timezone = "Asia/Shanghai";
            var proposal = component.proposals().propose(actor, input);
            var plan = proposal.plans.get(0);
            assertEquals("assistant.reminder", plan.type); assertEquals("17:00", plan.schedule.time);
            assertNull(plan.id); assertFalse(plan.enabled);
            assertEquals(1, plan.parameters.size()); assertTrue(plan.parameters.has("text"));
            assertEquals(before, component.store().taskCount(actor.getUserId()));
            assertFalse(outbound.get().has("tools"));
            assertEquals("test-model", outbound.get().get("model").getAsString());
        } finally { provider.stop(0); }
    }
}
