package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.ai.AssistantChatController;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class AIChatAssemblyTest {
    @Test(timeout = 30000)
    public void approvedAssistantCreateBindsTheCurrentWritingConversation() throws Exception {
        var providerCalls = new AtomicInteger();
        var gson = new Gson();
        var firstAnswer = "Let's outline this draft.";
        var finalAnswer = "I saved your draft.";
        var create =
                Map.of(
                        "title",
                        "Assistant-created draft",
                        "typeId",
                        1,
                        "content",
                        "<p>Reviewed draft text</p>",
                        "status",
                        "draft");
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext(
                "/chat/completions",
                exchange -> {
                    exchange.getRequestBody().readAllBytes();
                    int call = providerCalls.incrementAndGet();
                    var delta =
                            call == 2
                                    ? Map.of(
                                            "tool_calls",
                                            List.of(
                                                    Map.of(
                                                            "index",
                                                            0,
                                                            "id",
                                                            "call-create",
                                                            "type",
                                                            "function",
                                                            "function",
                                                            Map.of(
                                                                    "name",
                                                                    "create_article",
                                                                    "arguments",
                                                                    gson.toJson(create)))))
                                    : Map.of("content", call == 1 ? firstAnswer : finalAnswer);
                    String choice =
                            gson.toJson(
                                    Map.of(
                                            "choices",
                                            List.of(
                                                    Map.of(
                                                            "delta",
                                                            delta,
                                                            "finish_reason",
                                                            call == 2 ? "tool_calls" : "stop"))));
                    byte[] reply =
                            ("data: " + choice + "\n\ndata: [DONE]\n\n")
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, reply.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(reply);
                    }
                });
        provider.start();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_provider", "DEEP_SEEK");
            db.putWebsite("ai_model", "test-model");
            db.putWebsite("ai_base_url", "http://127.0.0.1:" + provider.getAddress().getPort());
            db.execute(
                    "update user set preferences=? where userId=1",
                    "{\"assistant\":{\"knowledgeScope\":\"own_all\"}}");
            db.loadAdminWebModules();

            var wire = new AtomicReference<String>();
            String sessionId =
                    controller(db, "openDraftConversation", "draft", Map.of(), null, wire)
                            .openDraftConversation()
                            .getData()
                            .conversationId;
            var input = new AIChatModels.ChatRequest();
            input.articleId = 0L;
            input.sessionId = sessionId;
            input.input = "Let's prepare a draft.";
            controller(db, "ai", "", Map.of(), gson.toJson(input), wire).ai();
            assertEquals(firstAnswer, event(wire.get(), "answer").get("content").getAsString());
            assertEquals(0, ((Number) db.scalar("select count(*) from log")).intValue());

            input.input = "Create the draft article now.";
            controller(db, "ai", "", Map.of(), gson.toJson(input), wire).ai();
            var paused = event(wire.get(), "approval-required").getAsJsonObject("run");
            assertEquals(0, ((Number) db.scalar("select count(*) from log")).intValue());

            var approval = new AIChatModels.ApprovalRequest();
            approval.articleId = 0L;
            approval.sessionId = sessionId;
            approval.runId = paused.get("runId").getAsString();
            approval.approvalId = paused.getAsJsonObject("approval").get("id").getAsString();
            approval.decision = "approve";
            controller(db, "approveAiOperation", "approval", Map.of(), gson.toJson(approval), wire)
                    .approveAiOperation();
            long articleId = event(wire.get(), "article-updated").get("articleId").getAsLong();
            assertEquals(finalAnswer, event(wire.get(), "answer").get("content").getAsString());
            assertNotNull(event(wire.get(), "done"));
            assertEquals(3, providerCalls.get());
            assertEquals(
                    1,
                    ((Number) db.scalar("select count(*) from log where rubbish=true")).intValue());
            var messages =
                    controller(
                                    db,
                                    "conversation",
                                    "conversation",
                                    Map.of("id", Long.toString(articleId), "sessionId", sessionId),
                                    null,
                                    wire)
                            .conversation()
                            .getData()
                            .aiMessages;
            var contents =
                    messages.stream()
                            .map(message -> message.getContent())
                            .collect(java.util.stream.Collectors.toList());
            assertTrue(contents.toString(), contents.contains(firstAnswer));
            assertTrue(contents.toString(), contents.contains(finalAnswer));
        } finally {
            provider.stop(0);
        }
    }

    private static JsonObject event(String wire, String type) {
        return wire.lines()
                .filter(line -> line.startsWith("data: "))
                .map(line -> JsonParser.parseString(line.substring(6)).getAsJsonObject())
                .filter(value -> type.equals(value.get("type").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + type + ": " + wire));
    }

    private AssistantChatController controller(
            InMemoryZrLogDatabase db,
            String methodName,
            String path,
            Map<String, String> params,
            String body,
            AtomicReference<String> wire)
            throws Exception {
        HttpRequest request =
                (HttpRequest)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {HttpRequest.class},
                                (proxy, method, args) -> {
                                    switch (method.getName()) {
                                        case "getServerConfig":
                                            return db.config().getServerConfig();
                                        case "getParaToStr":
                                            return params.getOrDefault(
                                                    args[0],
                                                    args.length > 1 ? (String) args[1] : null);
                                        case "getRequestBodyByteBuffer":
                                            return body == null
                                                    ? null
                                                    : ByteBuffer.wrap(
                                                            body.getBytes(StandardCharsets.UTF_8));
                                        case "getInputStream":
                                            return body == null
                                                    ? null
                                                    : new ByteArrayInputStream(
                                                            body.getBytes(StandardCharsets.UTF_8));
                                        case "getUri":
                                            return "/api/admin/assistant/chat"
                                                    + (path.isEmpty() ? "" : "/" + path);
                                        case "getRemoteHost":
                                            return "127.0.0.1";
                                        case "getContextPath":
                                            return "";
                                        case "getHeaderMap":
                                            return Map.of();
                                        case "getHeader":
                                            return null;
                                        default:
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                    }
                                });
        Map<String, String> headers = new HashMap<>();
        HttpResponse response =
                (HttpResponse)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {HttpResponse.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getHeader")) return headers;
                                    if (method.getName().equals("write")) {
                                        try (InputStream in = (InputStream) args[0]) {
                                            wire.set(
                                                    new String(
                                                            in.readAllBytes(),
                                                            StandardCharsets.UTF_8));
                                        }
                                        assertEquals(wire.get(), 200, args[1]);
                                    }
                                    return null;
                                });
        return (AssistantChatController)
                Controller.buildController(
                        AssistantChatController.class.getMethod(methodName), request, response);
    }

    @Test(timeout = 30000)
    public void productionRequestGraphStreamsAndRestoresHistoryAcrossControllers()
            throws Exception {
        List<JsonObject> requests = Collections.synchronizedList(new ArrayList<>());
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext(
                "/chat/completions",
                exchange -> {
                    requests.add(
                            JsonParser.parseString(
                                            new String(
                                                    exchange.getRequestBody().readAllBytes(),
                                                    StandardCharsets.UTF_8))
                                    .getAsJsonObject());
                    byte[] reply =
                            ("data: {\"choices\":[{\"delta\":{\"content\":\"Answer "
                                            + requests.size()
                                            + "\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                    exchange.sendResponseHeaders(200, reply.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(reply);
                    }
                });
        provider.start();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_provider", "DEEP_SEEK");
            db.putWebsite("ai_model", "test-model");
            db.putWebsite("ai_base_url", "http://127.0.0.1:" + provider.getAddress().getPort());
            db.execute(
                    "update user set preferences=? where userId=1",
                    "{\"assistant\":{\"knowledgeScope\":\"off\"}}");
            db.loadAdminWebModules();
            for (int turn = 1; turn <= 2; turn++) {
                Map<String, String> params =
                        Map.of("id", "0", "input", "Question " + turn, "tool", "");
                AtomicReference<String> wire = new AtomicReference<>();
                controller(db, "ai", "", params, null, wire).ai();
                assertTrue(wire.get(), wire.get().contains("\"type\":\"done\""));
                assertTrue(wire.get(), wire.get().contains("Answer " + turn));
                assertFalse(wire.get(), wire.get().contains("\"type\":\"error\""));
            }
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).toString().contains("Question 1"));
            assertTrue(requests.get(1).toString().contains("Answer 1"));
            String stored =
                    String.valueOf(
                            db.scalar(
                                    "select value from website where name='ai_chat_message_u1_-1'"));
            assertTrue(stored, stored.contains("Question 1"));
            assertTrue(stored, stored.contains("Answer 2"));
        } finally {
            provider.stop(0);
        }
    }
}
