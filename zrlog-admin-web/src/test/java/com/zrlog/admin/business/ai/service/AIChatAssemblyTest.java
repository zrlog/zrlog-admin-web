package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.ai.AIArticleController;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AIChatAssemblyTest {
    @Test(timeout = 30000)
    public void productionRequestGraphStreamsAndRestoresHistoryAcrossControllers() throws Exception {
        List<JsonObject> requests = Collections.synchronizedList(new ArrayList<>());
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/chat/completions", exchange -> {
            requests.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] reply = ("data: {\"choices\":[{\"delta\":{\"content\":\"Answer " + requests.size()
                    + "\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
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
            db.execute("update user set preferences=? where userId=1",
                    "{\"assistant\":{\"knowledgeScope\":\"off\"}}");
            db.loadAdminWebModules();
            for (int turn = 1; turn <= 2; turn++) {
                Map<String, String> params = Map.of("id", "0", "input", "Question " + turn, "tool", "");
                HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "getServerConfig": return db.config().getServerConfig();
                                case "getParaToStr": return params.getOrDefault(args[0], args.length > 1 ? (String) args[1] : null);
                                case "getUri": return "/api/admin/article/ai";
                                case "getRemoteHost": return "127.0.0.1";
                                case "getContextPath": return "";
                                case "getHeaderMap": return Map.of();
                                case "getHeader": return null;
                                default: throw new UnsupportedOperationException(method.getName());
                            }
                        });
                AtomicReference<String> wire = new AtomicReference<>();
                Map<String, String> headers = new HashMap<>();
                HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> {
                            if (method.getName().equals("getHeader")) return headers;
                            if (method.getName().equals("write")) {
                                assertEquals(200, args[1]);
                                try (InputStream in = (InputStream) args[0]) {
                                    wire.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                                }
                            }
                            return null;
                        });
                AIArticleController controller = (AIArticleController) Controller.buildController(
                        AIArticleController.class.getMethod("ai"), request, response);
                controller.ai();
                assertTrue(wire.get(), wire.get().contains("\"type\":\"done\""));
                assertTrue(wire.get(), wire.get().contains("Answer " + turn));
                assertFalse(wire.get(), wire.get().contains("\"type\":\"error\""));
            }
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).toString().contains("Question 1"));
            assertTrue(requests.get(1).toString().contains("Answer 1"));
            String stored = String.valueOf(db.scalar("select value from website where name='ai_chat_message_u1_-1'"));
            assertTrue(stored, stored.contains("Question 1"));
            assertTrue(stored, stored.contains("Answer 2"));
        } finally {
            provider.stop(0);
        }
    }
}
