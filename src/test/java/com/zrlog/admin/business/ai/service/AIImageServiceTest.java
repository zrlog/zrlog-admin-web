package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.GenerateArticleCoverResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AIImageServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    public void shouldFallbackImageSizeAndPersistB64CoverFromRealWebsiteConfig() throws Exception {
        byte[] coverBytes = "cover-bytes".getBytes(StandardCharsets.UTF_8);
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedImageConfig(db, "16:9");
            FakeHttpClient client = new FakeHttpClient(
                    stringResponse(500, "{\"error\":{\"message\":\"size not supported\"}}"),
                    imageB64Response(coverBytes)
            );

            GenerateArticleCoverResponse response = new AIImageService(client)
                    .generateArticleCover(articleRequest());

            assertEquals("png", response.getExtension());
            assertEquals("image/png", response.getMimeType());
            assertTrue(response.getUrl().startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/ai/article-cover-"));
            assertStoredDbFile(db, response.getUrl(), coverBytes);
            assertImageRequest(client, 0);
            assertImageRequest(client, 1);
            assertTrue(client.requestBodies.get(0).contains("\"size\":\"1536x1024\""));
            assertTrue(client.requestBodies.get(1).contains("\"size\":\"1024x1024\""));
            assertTrue(client.requestBodies.get(1).contains("\"model\":\"gpt-image-2\""));
            assertTrue(client.requestBodies.get(1).contains("Selected text from the current editor selection"));
        }
    }

    @Test
    public void shouldDownloadProviderImageUrlAndPersistCoverFromRealWebsiteConfig() throws Exception {
        byte[] coverBytes = "downloaded-webp".getBytes(StandardCharsets.UTF_8);
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedImageConfig(db, "1:1");
            FakeHttpClient client = new FakeHttpClient(
                    imageUrlResponse("https://cdn.example.com/generated-cover.webp?x=1"),
                    byteResponse(200, coverBytes, "image/webp;charset=utf-8")
            );

            GenerateArticleCoverResponse response = new AIImageService(client)
                    .generateArticleCover(articleRequest());

            assertEquals("webp", response.getExtension());
            assertEquals("image/webp", response.getMimeType());
            assertStoredDbFile(db, response.getUrl(), coverBytes);
            assertImageRequest(client, 0);
            assertEquals("GET", client.requests.get(1).method());
            assertEquals("https://cdn.example.com/generated-cover.webp?x=1", client.requests.get(1).uri().toString());
            assertTrue(client.requestBodies.get(0).contains("\"size\":\"1024x1024\""));
            assertEquals("", client.requestBodies.get(1));
        }
    }

    @Test
    public void shouldUseCustomImageBaseUrlAndModelWithoutAuthorizationHeader() throws Exception {
        byte[] coverBytes = "custom-cover".getBytes(StandardCharsets.UTF_8);
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedImageConfig(db, "1:1");
            db.putWebsite("ai_image_base_url", "http://localhost:11434/v1/");
            db.putWebsite("ai_image_model", "local-image:latest");
            db.putWebsite("ai_image_api_key", "");
            FakeHttpClient client = new FakeHttpClient(imageB64Response(coverBytes));

            new AIImageService(client).generateArticleCover(articleRequest());

            HttpRequest request = client.requests.get(0);
            assertEquals("http://localhost:11434/v1/images/generations", request.uri().toString());
            assertFalse(request.headers().firstValue("Authorization").isPresent());
            assertTrue(client.requestBodies.get(0).contains("\"model\":\"local-image:latest\""));
        }
    }

    @Test
    public void shouldRejectUnknownImageModelForProviderDefaultEndpoint() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedImageConfig(db, "1:1");
            db.putWebsite("ai_image_model", "local-image:latest");

            assertThrows(UnsupportedAIImageGenerationException.class,
                    () -> new AIImageService(new FakeHttpClient()).generateArticleCover(articleRequest()));
        }
    }

    private static void seedImageConfig(InMemoryZrLogDatabase db, String aspectRatio) throws Exception {
        db.putWebsite("ai_image_provider", "OPEN_AI");
        db.putWebsite("ai_image_model", "gpt-image-2");
        db.putWebsite("ai_image_base_url", "");
        db.putWebsite("ai_image_api_key", "image-key");
        db.putWebsite("article_cover_aspect_ratio", aspectRatio);
    }

    private GenerateArticleFieldRequest articleRequest() {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setTitle("Cover title");
        request.setDigest("Cover digest");
        request.setKeywords("java,zrlog");
        request.setMarkdown(repeat("Markdown paragraph for cover prompt. ", 50));
        request.setSelectedText("selected cover paragraph");
        return request;
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static void assertStoredDbFile(InMemoryZrLogDatabase db, String url, byte[] bytes) throws Exception {
        Map<String, Object> row = db.queryOne("select value from website where name=?", "db_file" + url);
        assertEquals(Base64.getEncoder().encodeToString(bytes), row.get("value"));
    }

    private static void assertImageRequest(FakeHttpClient client, int index) {
        HttpRequest request = client.requests.get(index);
        assertEquals("POST", request.method());
        assertEquals("https://api.openai.com/v1/images/generations", request.uri().toString());
        assertEquals("Bearer image-key", request.headers().firstValue("Authorization").orElse(""));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElse(""));
    }

    private static HttpResponse<String> imageB64Response(byte[] bytes) {
        return stringResponse(200, GSON.toJson(Map.of("data",
                List.of(Map.of("b64_json", Base64.getEncoder().encodeToString(bytes))))));
    }

    private static HttpResponse<String> imageUrlResponse(String url) {
        return stringResponse(200, GSON.toJson(Map.of("data", List.of(Map.of("url", url)))));
    }

    private static HttpResponse<String> stringResponse(int statusCode, String body) {
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
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public String body() {
                return body;
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

    private static HttpResponse<byte[]> byteResponse(int statusCode, byte[] body, String contentType) {
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
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
            }

            @Override
            public byte[] body() {
                return body;
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

    private static String readBody(HttpRequest request) {
        return request.bodyPublisher().map(AIImageServiceTest::readBody).orElse("");
    }

    private static String readBody(HttpRequest.BodyPublisher publisher) {
        BodyCollector collector = new BodyCollector();
        publisher.subscribe(collector);
        return collector.body();
    }

    private static class FakeHttpClient extends HttpClient {

        private final List<HttpResponse<?>> responses;
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> requestBodies = new ArrayList<>();
        private int index;

        private FakeHttpClient(HttpResponse<?>... responses) {
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
            requestBodies.add(readBody(request));
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

    private static class BodyCollector implements Flow.Subscriber<ByteBuffer> {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final CountDownLatch done = new CountDownLatch(1);
        private Throwable error;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            out.write(bytes, 0, bytes.length);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        private String body() {
            try {
                if (!done.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Request body was not published");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            if (error != null) {
                throw new AssertionError(error);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
