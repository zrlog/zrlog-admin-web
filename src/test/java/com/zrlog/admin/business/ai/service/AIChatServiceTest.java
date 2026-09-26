package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AIChatServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    public void shouldStartStreamResponseThroughPublicOverloadsUsingRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"plan \","
                            + "\"content\":\"first\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"second\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse first = service.startStreamResponse("Question", 36L);
            String firstPayload = new String(first.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            AIStreamResponse second = service.startStreamResponse("Question", 37L, null, null);
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
            seedAiConfig(db);
            db.putWebsite("ai_reasoning_enabled", false);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\","
                            + "\"content\":\"answer\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Question", 39L);
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
    public void shouldStartStreamResponseContinueAndPersistMessagesThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"part \"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"done\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Question", 33L, null, null, false);
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
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Question", 38L, null, null, true);
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
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    streamResponse(503, "{\"error\":{\"message\":\"busy\"}}"),
                    streamResponse("data: {\"choices\":[{\"delta\":{\"content\":\"recovered\"}}]}\n\n"
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Retry", 34L, null, null, true);
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(200, response.getStatusCode());
            assertEquals(2, client.requests.size());
            assertTrue(payload.contains("recovered"));
        }
    }

    @Test
    public void shouldReturnSafeProviderErrorWhenInitialStreamRequestFails() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(streamResponse(429, "{\"error\":{\"message\":\"quota\"}}"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Retry", 35L, null, null, true);

            assertEquals(200, response.getStatusCode());
            String payload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload.contains("\"error\":\"providerRequestFailed\""));
            assertFalse(payload.contains("quota"));
        }
    }

    private static HttpResponse<InputStream> streamResponse(String body) {
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
            db.execute("insert into log(logId,userId,typeId,title,rubbish,privacy) values(?,?,?,?,?,?)", id,1,1,"Article",false,false);
        }
    }

    private static AIWebSiteInfoWithAIMessages providerInfo() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        info.setAi_api_key("test-key");
        return info;
    }

    private static class NoSleepAIChatService extends AIChatService {

        private NoSleepAIChatService(HttpClient client) {
            super(client);
        }

        @Override
        void pauseBeforeStreamRetry(int attempt) {
        }
    }

    private static class FakeHttpClient extends HttpClient {

        private final List<HttpResponse<InputStream>> responses;
        private final List<HttpRequest> requests = new ArrayList<>();
        private int index;

        private FakeHttpClient(HttpResponse<InputStream>... responses) {
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
