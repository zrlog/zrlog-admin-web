package com.zrlog.admin.business.ai.service;

import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.ExecutionDeadline;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AIChatModelDeadlineTest {
    private final AccountAccess actor = AccountAccess.from(Map.of("userId", 1, "role", "owner", "enabled", true, "authVersion", 0));

    @Test(timeout = 10000)
    public void continuationSharesTheBudgetAndAnIncompleteBodyCannotKeepTheRequestAlive() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int count = requests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if (count == 1) {
                byte[] body = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"Partial\"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write('{'); out.flush();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            long start = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> new AIChatModelClient(new AIFileService()).completeTurn(
                    actor, info(server, AIProviderType.DEEP_SEEK), List.of(new AIProviderRequests.Message("user", "Prepare a report")),
                    List.of(), (type, text) -> { }, ExecutionDeadline.afterMillis(1000)));
            assertEquals(2, requests.get());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test(timeout = 10000)
    public void retryCannotStartWhenItsBackoffWouldExceedTheRemainingBudget() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes(); requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        server.start();
        try {
            assertThrows(HttpTimeoutException.class, () -> new AIChatModelClient(new AIFileService()).completeTurn(
                    actor, info(server, AIProviderType.DEEP_SEEK), List.of(new AIProviderRequests.Message("user", "Prepare a report")),
                    List.of(), (type, text) -> { }, ExecutionDeadline.afterMillis(1000)));
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }

    @Test(timeout = 10000)
    public void boundedOpenAiResponsesUsesTheExistingProtocolAdapter() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\","
                    + "\"content\":[{\"type\":\"output_text\",\"text\":\"A useful weekly report\"}]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var choice = new AIChatModelClient(new AIFileService()).completeTurn(actor, info(server, AIProviderType.OPEN_AI),
                    List.of(new AIProviderRequests.Message("user", "Prepare a report")), List.of(), (type, text) -> { },
                    ExecutionDeadline.afterMillis(3000));
            assertEquals("A useful weekly report", choice.getMessage().getContent());
            assertEquals("stop", choice.getFinishReason());
        } finally { server.stop(0); }
    }

    private AIWebSiteInfo info(HttpServer server, AIProviderType provider) {
        var info = new AIWebSiteInfo();
        info.setAi_provider(provider); info.setAi_model("test-model");
        info.setAi_base_url("http://127.0.0.1:" + server.getAddress().getPort());
        return info;
    }
}
