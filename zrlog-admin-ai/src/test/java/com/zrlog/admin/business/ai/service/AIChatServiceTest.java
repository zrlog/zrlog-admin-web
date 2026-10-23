package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.model.AIChatModels.ChatRequest;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.ZrLogConfig;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.Assert.*;

public class AIChatServiceTest {

    private static final Gson GSON = new Gson();
    private ZrLogConfig applicationConfig;

    static HttpResponse<InputStream> streamResponse(String body) {
        return streamResponse(200, body);
    }

    private static HttpResponse<InputStream> streamResponse(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("Content-Type", List.of("text/event-stream")), (name, value) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static void seedAiConfig(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("ai_provider", "DEEP_SEEK");
        db.putWebsite("ai_model", "deepseek-chat");
        db.putWebsite("ai_api_key", "test-key");
        db.putWebsite("ai_prompt", "System prompt");
        for (int id = 33; id <= 39; id++) {
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy) values(?,?,?,?,?,?)", id, 1, 1, "Article", false, false);
        }
    }

    private static AIWebSiteInfoWithAIMessages providerInfo() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        info.setAi_api_key("test-key");
        return info;
    }

    @Test
    public void streamingFragmentsAndContinuationDoNotRepeatRequestAuthorizationQueries() throws Exception {
        List<Integer> queryCounts = new ArrayList<>();
        for (int fragments : List.of(1, 500)) {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
                seedAiConfig(db);
                db.execute("update user set preferences=? where userId=1", "{\"assistant\":{\"knowledgeScope\":\"off\"}}");
                StringBuilder frames = new StringBuilder();
                for (int i = 0; i < fragments; i++) {
                    frames.append("data: {\"choices\":[{\"delta\":{\"content\":\"x\",\"reasoning_content\":\"r\"}}]}\n\n");
                }
                frames.append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n");
                FakeHttpClient client = new FakeHttpClient(streamResponse(frames.toString()),
                        streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"done\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"));
                var service = chat(db.config(), client);
                var actor = AIServiceDependencies.actor(db.dataSource());
                var audit = AIServiceDependencies.audit(db.dataSource());
                List<String> queries = Collections.synchronizedList(new ArrayList<>());
                db.afterWebApiQuery((sql, params) -> queries.add(sql));

                String wire = new String(service.startStreamResponse(actor, "Question", 36L, audit, null)
                        .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                assertTrue(wire, wire.contains("\"type\":\"done\""));
                assertTrue(wire, wire.contains("\"content\":\"" + "x".repeat(fragments) + "done\""));
                assertTrue(wire, wire.contains("\"reasoningContent\":\"" + "r".repeat(fragments) + "\""));
                assertEquals(2, client.requests.size());
                assertEquals(1, queries.stream().filter(sql -> sql.startsWith("select * from user ")).count());
                assertEquals(1, queries.stream().filter(sql -> sql.startsWith("select preferences from user ")).count());
                queryCounts.add(queries.size());
                String stored = Objects.toString(db.scalar("select value from website where name='ai_chat_message_u1_36'"));
                assertTrue(stored, stored.contains("x".repeat(fragments) + "done"));
                assertTrue(stored, stored.contains("r".repeat(fragments)));
            }
        }
        assertEquals(queryCounts.get(0), queryCounts.get(1));
    }

    @Test
    public void shouldReplayPersistedOpenAiHistoryAcrossConsecutiveUserTurns() throws Exception {
        for (boolean enabled : List.of(true, false))
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                applicationConfig = db.config();
                seedAiConfig(db);
                db.putWebsite("ai_provider", "OPEN_AI");
                db.putWebsite("ai_model", "gpt-6-astra");
                db.putWebsite("ai_reasoning_enabled", enabled);
                FakeHttpClient client = new FakeHttpClient(
                        streamResponse(OpenAIResponsesAdapterTest.terminal(OpenAIResponsesAdapterTest.MESSAGE.replace("Answer", "Answer 1"))),
                        streamResponse(OpenAIResponsesAdapterTest.terminal(OpenAIResponsesAdapterTest.MESSAGE.replace("Answer", "Answer 2"))),
                        streamResponse(OpenAIResponsesAdapterTest.terminal(OpenAIResponsesAdapterTest.MESSAGE.replace("Answer", "Answer 3"))));
                for (int turn = 1; turn <= 3; turn++) {
                    ChatRequest input = new ChatRequest();
                    input.articleId = 36;
                    input.input = "Question " + turn;
                    // Each browser request creates a new service and restores history from storage.
                    String payload = new String(chat(applicationConfig, client).start(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), input, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser())
                            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(payload, payload.contains("\"type\":\"done\""));
                    assertFalse(payload, payload.contains("\"type\":\"error\""));
                    assertTrue(payload, payload.contains("Answer " + turn));
                    JsonArray items = client.requestBodies.get(turn - 1).getAsJsonArray("input");
                    int assistants = 0;
                    for (int index = 0; index < items.size(); index++) {
                        JsonObject item = items.get(index).getAsJsonObject();
                        JsonObject content = item.getAsJsonArray("content").get(0).getAsJsonObject();
                        if ("assistant".equals(item.get("role").getAsString())) {
                            assistants++;
                            assertEquals("output_text", content.get("type").getAsString());
                            assertEquals("Answer " + assistants, content.get("text").getAsString());
                            JsonObject question = items.get(index - 1).getAsJsonObject();
                            assertEquals("user", question.get("role").getAsString());
                            assertEquals("Question " + assistants, question.getAsJsonArray("content").get(0)
                                    .getAsJsonObject().get("text").getAsString());
                        } else {
                            assertEquals("input_text", content.get("type").getAsString());
                        }
                    }
                    assertEquals(turn - 1, assistants);
                    assertEquals(input.input, items.get(items.size() - 1).getAsJsonObject()
                            .getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
                }
                assertEquals(3, client.requests.size());
                String stored = String.valueOf(db.queryOne("select value from website where name=?", "ai_chat_message_u1_36").get("value"));
                for (int turn = 1; turn <= 3; turn++) {
                    assertTrue(stored.contains("Question " + turn));
                    assertTrue(stored.contains("Answer " + turn));
                }
            }
    }

    @Test
    public void shouldStreamAndPersistOpenAiSummariesThroughTheExistingBrowserProtocol() throws Exception {
        for (boolean enabled : List.of(true, false))
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                applicationConfig = db.config();
                seedAiConfig(db);
                db.putWebsite("ai_provider", "OPEN_AI");
                db.putWebsite("ai_model", "gpt-6-astra");
                db.putWebsite("ai_base_url", "https://gateway.example/v1/chat/completions?route=test");
                db.putWebsite("ai_max_completion_tokens", 8192);
                db.putWebsite("ai_reasoning_enabled", enabled);
                FakeHttpClient client = new FakeHttpClient(streamResponse(
                        OpenAIResponsesAdapterTest.frame("{\"type\":\"response.reasoning_summary_text.delta\",\"item_id\":\"rs_1\",\"summary_index\":0,\"delta\":\"检查文章🙂\"}")
                                + OpenAIResponsesAdapterTest.frame("{\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}")
                                + OpenAIResponsesAdapterTest.terminal(OpenAIResponsesAdapterTest.REASONING + "," + OpenAIResponsesAdapterTest.MESSAGE)));
                String payload = new String(chat(applicationConfig, client).startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 36L, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser())
                        .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String stored = String.valueOf(db.queryOne("select value from website where name=?", "ai_chat_message_u1_36").get("value"));
                assertEquals("https://gateway.example/v1/responses?route=test", client.requests.get(0).uri().toString());
                JsonObject request = client.requestBodies.get(0);
                assertEquals(enabled, request.has("reasoning"));
                assertEquals("gpt-6-astra", request.get("model").getAsString());
                assertEquals(8192, request.get("max_output_tokens").getAsInt());
                assertTrue(request.has("input"));
                assertFalse(request.has("messages"));
                assertTrue(payload, payload.contains("\"type\":\"delta\""));
                assertTrue(payload, payload.contains("\"type\":\"answer\""));
                assertTrue(payload, payload.contains("\"type\":\"done\""));
                assertEquals(enabled, payload.contains("\"type\":\"reasoning_delta\""));
                assertEquals(enabled, stored.contains("检查文章🙂"));
                assertTrue(stored.contains("Answer"));
                assertFalse(payload.contains("opaque-state"));
                assertFalse(stored.contains("opaque-state"));
            }
    }

    @Test
    public void shouldReplayResponsesContextAcrossBoundedContinuation() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-6-astra");
            String partial = OpenAIResponsesAdapterTest.completed(OpenAIResponsesAdapterTest.REASONING + "," + OpenAIResponsesAdapterTest.MESSAGE)
                    .replace("\"status\":\"completed\",\"output\"", "\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\"");
            FakeHttpClient client = new FakeHttpClient(streamResponse(OpenAIResponsesAdapterTest.frame("{\"type\":\"response.incomplete\",\"response\":" + partial + "}")),
                    streamResponse(OpenAIResponsesAdapterTest.terminal(OpenAIResponsesAdapterTest.MESSAGE.replace("Answer", " continued"))));
            String payload = new String(chat(applicationConfig, client).startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 36L, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser())
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload, payload.contains("\"content\":\"Answer continued\""));
            assertTrue(client.requestBodies.get(1).toString().contains("opaque-state"));
            assertTrue(client.requestBodies.get(1).toString().contains("final_answer"));
            assertFalse(client.requestBodies.get(1).has("tools"));
            assertFalse(payload.contains("opaque-state"));
        }
    }

    @Test
    public void shouldStartStreamResponseThroughPublicOverloadsUsingRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"plan \","
                            + "\"content\":\"first\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"second\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse first = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 36L, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String firstPayload = new String(first.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            AIStreamResponse second = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 37L, null, null, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String secondPayload = new String(second.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(200, first.getStatusCode());
            assertEquals(200, second.getStatusCode());
            assertTrue(client.requests.stream().allMatch(request -> request.timeout().isEmpty()));
            assertTrue(firstPayload.contains("first"));
            assertTrue(firstPayload.contains("\"reasoningContent\":\"plan \""));
            assertTrue(secondPayload.contains("second"));
            String firstStored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_u1_36").get("value"));
            assertTrue(firstStored.contains("first"));
            assertTrue(firstStored.contains("\"reasoningContent\":\"plan \""));
            assertTrue(String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_u1_37").get("value")).contains("second"));
        }
    }

    @Test
    public void shouldDropProviderReasoningWhenDisabledThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            db.putWebsite("ai_reasoning_enabled", false);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\","
                            + "\"content\":\"answer\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse response = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 39L, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_u1_39").get("value"));

            assertEquals(200, response.getStatusCode());
            assertTrue(payload.contains("answer"));
            assertFalse(payload.contains("reasoningContent"));
            assertTrue(stored.contains("answer"));
            assertFalse(stored.contains("reasoningContent"));
        }
    }

    @Test
    public void shouldPersistAnswerWhenProviderSendsAnEmptyUsageDeltaAfterFinishing() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(streamResponse(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Answer🙂\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: {\"error\":null,\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                            + "\"usage\":{\"completion_tokens\":3}}\n\n"
                            + "data: [DONE]\n\n"));
            String payload = new String(chat(applicationConfig, client).startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 36L, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser())
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload, payload.contains("\"type\":\"delta\""));
            assertTrue(payload, payload.contains("\"type\":\"answer\""));
            assertTrue(payload, payload.contains("\"type\":\"done\""));
            assertFalse(payload, payload.contains("\"type\":\"error\""));
            assertEquals(1, client.requests.size());
            String stored = String.valueOf(db.queryOne("select value from website where name=?", "ai_chat_message_u1_36").get("value"));
            assertTrue(stored, stored.contains("Answer🙂"));
        }
    }

    @Test
    public void shouldStartStreamResponseContinueAndPersistMessagesThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part \"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"done\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse response = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 33L, null, null, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_u1_33").get("value"));

            assertEquals(200, response.getStatusCode());
            assertEquals(2, client.requests.size());
            assertTrue(payload.contains("\"content\":\"part \""));
            assertTrue(payload.contains("\"content\":\"done\""));
            assertTrue(stored.contains("\"role\":\"system\""));
            assertTrue(stored.contains("\"role\":\"user\""));
            assertTrue(stored.contains("\"role\":\"assistant\""));
            assertTrue(stored.contains("part done"));
            assertTrue(stored.contains("\"provider\":\"DEEP_SEEK\""));
            assertTrue(stored.contains("\"model\":\"deepseek-chat\""));
        }
    }

    @Test
    public void shouldEmitIncompleteErrorWhenContinuationRoundsAreExhaustedThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part0\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part1\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part2\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part3\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse response = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Question", 38L, null, null, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(200, response.getStatusCode());
            assertEquals(4, client.requests.size());
            assertTrue(payload.contains("\"content\":\"part0\""));
            assertTrue(payload.contains("\"content\":\"part3\""));
            assertTrue(payload.contains("\"type\":\"error\""));
            assertTrue(payload.contains("\"error\":\"responseIncomplete\""));
            assertEquals(null, db.queryOne("select value from website where name=?", "ai_chat_message_u1_38"));
        }
    }

    @Test
    public void shouldRetryInitialStreamRequestWithoutWaitingInTests() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse(503, "{\"error\":{\"message\":\"busy\"}}"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"recovered\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse response = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Retry", 34L, null, null, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(200, response.getStatusCode());
            assertEquals(2, client.requests.size());
            assertTrue(payload.contains("recovered"));
        }
    }

    @Test
    public void shouldReturnSafeProviderErrorWhenInitialStreamRequestFails() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(streamResponse(429, "{\"error\":{\"message\":\"quota\"}}"));
            AIChatService service = chat(applicationConfig, client);

            AIStreamResponse response = service.startStreamResponse(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), "Retry", 35L, null, null, com.zrlog.admin.business.ai.service.AIServiceDependencies.audit(db.dataSource()), com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());

            assertEquals(200, response.getStatusCode());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload.contains("\"error\":\"providerRequestFailed\""));
            assertFalse(payload.contains("quota"));
        }
    }

    private static AIChatService chat(ZrLogConfig config, HttpClient client) {
        var source = (DataSourceWrapper) config.getDataSource();
        AIChatModelClient model = new AIChatModelClient(client, new AIFileService()) {
            @Override
            void pauseBeforeStreamRetry(int attempt) {
            }
        };
        return AIServiceDependencies.chat(source, null, ContentToolsFixture.from(config), model,
                new AIFileService(), AIServiceDependencies.writing(source));
    }

    static class FakeHttpClient extends HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<JsonObject> requestBodies = new ArrayList<>();
        private final List<HttpResponse<InputStream>> responses;
        private int index;

        FakeHttpClient(HttpResponse<InputStream>... responses) {
            this.responses = Arrays.asList(responses);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            request.bodyPublisher()
                    .orElseThrow()
                    .subscribe(
                            new Flow.Subscriber<java.nio.ByteBuffer>() {
                                public void onSubscribe(Flow.Subscription subscription) {
                                    subscription.request(Long.MAX_VALUE);
                                }

                                public void onNext(java.nio.ByteBuffer buffer) {
                                    byte[] bytes = new byte[buffer.remaining()];
                                    buffer.get(bytes);
                                    body.writeBytes(bytes);
                                }

                                public void onError(Throwable error) {
                                    throw new AssertionError(error);
                                }

                                public void onComplete() {
                                }
                            });
            requestBodies.add(GSON.fromJson(body.toString(StandardCharsets.UTF_8), JsonObject.class));
            return (HttpResponse<T>) responses.get(index++);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }
}
