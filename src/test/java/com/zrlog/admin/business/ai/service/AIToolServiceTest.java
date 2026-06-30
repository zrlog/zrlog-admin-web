package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest;
import com.zrlog.admin.business.rest.request.OptimizeAiPromptRequest;
import com.zrlog.admin.business.rest.request.OptimizeWebsiteDescriptionRequest;
import com.zrlog.admin.business.rest.request.ScoreArticleRequest;
import com.zrlog.admin.business.rest.response.ArticleProofreadResponse;
import com.zrlog.admin.business.rest.response.ArticleReaderQuestionsResponse;
import com.zrlog.admin.business.rest.response.ArticleSeoCheckResponse;
import com.zrlog.admin.business.rest.response.ArticleStructureAdviceResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleAliasResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleDigestResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleMarkdownResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleTagsResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleTitleResponse;
import com.zrlog.admin.business.rest.response.OptimizeAiPromptResponse;
import com.zrlog.admin.business.rest.response.OptimizeWebsiteDescriptionResponse;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AIToolServiceTest {

    private static final Gson GSON = new Gson();

    @Test(expected = ArgsException.class)
    public void shouldRejectMarkdownRewriteWithoutDraftBody() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectMarkdownRewriteWhenDraftBodyIsTooShort() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setMarkdown(repeat("a", 119));

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test(expected = ArgsException.class)
    public void shouldTrimMarkdownBeforeRewriteLengthCheck() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setMarkdown("  " + repeat("a", 119) + "  ");

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test
    public void shouldSummarizeMarkdownReferencesForPublishCheck() {
        String summary = new AIToolService().buildMarkdownReferenceSummary(
                "![cover](/assets/cover.png)\n[site](https://example.com)\nhttps://zrlog.com/path");

        assertTrue(summary.contains("imageReferenceCount: 1"));
        assertTrue(summary.contains("- /assets/cover.png"));
        assertTrue(summary.contains("externalLinkCount: 2"));
        assertTrue(summary.contains("- https://example.com"));
        assertTrue(summary.contains("- https://zrlog.com/path"));
    }

    @Test
    public void shouldBuildPublishContextForPublishCheck() {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setAlias("release-check");
        request.setThumbnail("/attached/cover.png");
        request.setTransparentPublish(true);
        request.setStaticSiteEnabled(true);
        request.setStaticSitePluginEnabled(true);

        String summary = new AIToolService().buildPublishContextSummary(request);

        assertTrue(summary.contains("aliasStatus: present"));
        assertTrue(summary.contains("coverStatus: present"));
        assertTrue(summary.contains("staticSyncExpected: true"));
        assertTrue(summary.contains("structuredDataBoundary: theme-owned-public-output"));
        assertTrue(summary.contains("aiPublishCheckBlocksPublishing: false"));
    }

    @Test
    public void shouldRunAiToolsThroughRealWebsiteConfigAndProviderRequests() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(
                    completionResponse("Optimized description for the site."),
                    completionResponse("\"Optimized prompt\""),
                    completionResponse("{\"titles\":[\"One\",\"Two\",\"Three\",\"Four\"]}"),
                    completionResponse(scoreJson("Article score")),
                    completionResponse("{\"summary\":\"changed\",\"markdown\":\"## Rewrite\"}"),
                    completionResponse(scoreJson("Publish ok")),
                    completionResponse("{\"alias\":\"Hello, ZrLog 2026!\"}"),
                    completionResponse("{\"digest\":\"Digest value\"}"),
                    completionResponse("{\"tags\":[\"java\",\"blog\",\"java\"]}"),
                    completionResponse("{\"score\":76,\"summary\":\"SEO ok\",\"items\":["
                            + "{\"name\":\"title\",\"status\":\"warning\",\"suggestion\":\"shorten\"}]}"),
                    completionResponse("{\"summary\":\"Proofread ok\",\"items\":["
                            + "{\"original\":\"teh\",\"issue\":\"typo\",\"suggestion\":\"the\"}]}"),
                    completionResponse("{\"summary\":\"Structure ok\",\"items\":["
                            + "{\"name\":\"intro\",\"status\":\"good\",\"suggestion\":\"keep\"}]}"),
                    completionResponse("{\"summary\":\"Questions ok\",\"items\":["
                            + "{\"question\":\"Why?\",\"reason\":\"reader intent\",\"suggestion\":\"answer it\"}]}")
            );
            AIToolService service = new AIToolService(client);
            GenerateArticleFieldRequest articleRequest = articleFieldRequest();

            OptimizeWebsiteDescriptionResponse description =
                    service.optimizeWebsiteDescription(websiteDescriptionRequest());
            OptimizeAiPromptResponse prompt = service.optimizeAiPrompt(aiPromptRequest());
            GenerateArticleTitleResponse titles = service.generateArticleTitles(titleRequest(),
                    "previous assistant answer");
            ScoreArticleResponse score = service.scoreArticle(scoreRequest(), "score context");
            GenerateArticleMarkdownResponse markdown = service.rewriteArticleMarkdown(articleRequest,
                    "rewrite context");
            ScoreArticleResponse publish = service.publishCheckArticle(articleRequest, "publish context");
            GenerateArticleAliasResponse alias = service.generateArticleAlias(articleRequest, "alias context");
            GenerateArticleDigestResponse digest = service.generateArticleDigest(articleRequest, "digest context");
            GenerateArticleTagsResponse tags = service.generateArticleTags(articleRequest, "tags context");
            ArticleSeoCheckResponse seo = service.checkArticleSeo(articleRequest, "seo context");
            ArticleProofreadResponse proofread = service.proofreadArticle(articleRequest, "proofread context");
            ArticleStructureAdviceResponse structure = service.adviseArticleStructure(articleRequest,
                    "structure context");
            ArticleReaderQuestionsResponse questions = service.generateReaderQuestions(articleRequest,
                    "question context");

            assertEquals("Optimized description for the site.", description.getDescription());
            assertEquals("Optimized prompt", prompt.getPrompt());
            assertEquals(List.of("One", "Two", "Three"), titles.getTitles());
            assertEquals(88, score.getScore());
            assertEquals("Article score", score.getSummary());
            assertEquals("## Rewrite", markdown.getMarkdown());
            assertEquals("Publish ok", publish.getSummary());
            assertEquals("hello-zrlog-2026", alias.getAlias());
            assertEquals("Digest value", digest.getDigest());
            assertEquals(List.of("java", "blog"), tags.getTags());
            assertEquals("SEO ok", seo.getSummary());
            assertEquals("Proofread ok", proofread.getSummary());
            assertEquals("Structure ok", structure.getSummary());
            assertEquals("Questions ok", questions.getSummary());
            assertProviderRequests(client, 13);
            assertTrue(client.requestBodies.stream().allMatch(body -> body.contains("\"model\":\"deepseek-chat\"")));
            assertTrue(client.requestBodies.stream().allMatch(body -> body.contains("\"stream\":false")));
            assertTrue(client.requestBodies.stream().anyMatch(body -> body.contains("Selected text from the current editor selection")));
            assertTrue(client.requestBodies.stream().anyMatch(body -> body.contains("previous assistant answer")));
            assertTrue(client.requestBodies.stream().anyMatch(body -> body.contains("imageReferenceCount")));
            assertTrue(client.requestBodies.stream().anyMatch(body -> body.contains("staticSyncExpected")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldParseTitleTagMarkdownAndScoreResponses() throws Exception {
        AIToolService service = new AIToolService();

        List<String> titles = (List<String>) invoke(service, "parseTitles",
                "```json\n{\"titles\":[\" One \",\"\\\"Two\\\"\",\"Three\",\"Four\"]}\n```");
        List<String> tags = (List<String>) invoke(service, "parseTags",
                "{\"tags\":[\"java\",\"blog\",\"java\",\"\",\"zrlog\",\"ops\",\"test\",\"extra\"]}");
        GenerateArticleMarkdownResponse markdown = (GenerateArticleMarkdownResponse) invoke(service,
                "parseArticleMarkdown", "{\"summary\":\" \\\"changed\\\" \",\"markdown\":\"## Title\"}");
        ScoreArticleResponse score = (ScoreArticleResponse) invoke(service, "parseArticleScore",
                "{\"score\":101.4,\"summary\":\"Good\",\"items\":["
                        + "{\"name\":\"SEO\",\"score\":\"88.6\",\"suggestion\":\"Fix\"},"
                        + "\"invalid\",{\"name\":\"\",\"suggestion\":\"\"}]}");

        assertEquals(List.of("One", "Two", "Three"), titles);
        assertEquals(List.of("java", "blog", "zrlog", "ops", "test", "extra"), tags);
        assertEquals("changed", markdown.getSummary());
        assertEquals("## Title", markdown.getMarkdown());
        assertEquals(100, score.getScore());
        assertEquals("Good", score.getSummary());
        assertEquals(1, score.getItems().size());
        assertEquals("SEO", score.getItems().get(0).getName());
        assertEquals(89, score.getItems().get(0).getScore());
        assertEquals("Fix", score.getItems().get(0).getSuggestion());
    }

    @Test
    public void shouldParseArticleReviewToolResponses() throws Exception {
        AIToolService service = new AIToolService();

        ArticleSeoCheckResponse seo = (ArticleSeoCheckResponse) invoke(service, "parseArticleSeo",
                "{\"score\":\"76\",\"summary\":\"SEO ok\",\"items\":["
                        + "{\"name\":\"title\",\"status\":\"warning\",\"suggestion\":\"shorten\"}]}");
        ArticleProofreadResponse proofread = (ArticleProofreadResponse) invoke(service, "parseArticleProofread",
                "{\"summary\":\"Proofread ok\",\"items\":["
                        + "{\"original\":\"teh\",\"issue\":\"typo\",\"suggestion\":\"the\"}]}");
        ArticleStructureAdviceResponse structure = (ArticleStructureAdviceResponse) invoke(service,
                "parseArticleStructure",
                "{\"summary\":\"Structure ok\",\"items\":["
                        + "{\"name\":\"intro\",\"status\":\"good\",\"suggestion\":\"keep\"}]}");
        ArticleReaderQuestionsResponse questions = (ArticleReaderQuestionsResponse) invoke(service,
                "parseReaderQuestions",
                "{\"summary\":\"Questions ok\",\"items\":["
                        + "{\"question\":\"Why?\",\"reason\":\"reader intent\",\"suggestion\":\"answer it\"}]}");

        assertEquals(76, seo.getScore());
        assertEquals("SEO ok", seo.getSummary());
        assertEquals("title", seo.getItems().get(0).getName());
        assertEquals("warning", seo.getItems().get(0).getStatus());
        assertEquals("shorten", seo.getItems().get(0).getSuggestion());
        assertEquals("Proofread ok", proofread.getSummary());
        assertEquals("teh", proofread.getItems().get(0).getOriginal());
        assertEquals("typo", proofread.getItems().get(0).getIssue());
        assertEquals("the", proofread.getItems().get(0).getSuggestion());
        assertEquals("Structure ok", structure.getSummary());
        assertEquals("intro", structure.getItems().get(0).getName());
        assertEquals("good", structure.getItems().get(0).getStatus());
        assertEquals("keep", structure.getItems().get(0).getSuggestion());
        assertEquals("Questions ok", questions.getSummary());
        assertEquals("Why?", questions.getItems().get(0).getQuestion());
        assertEquals("reader intent", questions.getItems().get(0).getReason());
        assertEquals("answer it", questions.getItems().get(0).getSuggestion());
    }

    @Test
    public void shouldCleanAliasScoresAndPromptFragments() throws Exception {
        AIToolService service = new AIToolService();

        assertEquals("hello-zrlog-2026", invoke(service, "cleanAlias", " Hello, ZrLog 2026! "));
        assertEquals(0, invoke(service, "normalizeScore", -10));
        assertEquals(0, invoke(service, "normalizeScore", "bad"));
        assertEquals(51, invoke(service, "normalizeScore", "50.6"));
        assertEquals("", invoke(service, "valueToString", new Object[]{null}));
        assertEquals("42", invoke(service, "valueToString", 42));
        assertEquals("prompt\n\ncontext", invoke(service, "appendConversationContext", "prompt", "context"));
        assertEquals("prompt", invoke(service, "appendConversationContext", "prompt", ""));
        assertEquals("", invoke(service, "cleanPrompt", new Object[]{null}));
        assertEquals("quoted", invoke(service, "cleanPrompt", "\"quoted\""));
        assertEquals(160, invoke(service, "cleanDescription", repeat("a", 200)).toString().length());
        assertEquals("- none", invoke(service, "formatReferenceList", java.util.Set.of()));
    }

    @Test
    public void shouldLimitReferenceListOutput() throws Exception {
        AIToolService service = new AIToolService();
        Set<String> references = new LinkedHashSet<>();
        for (int i = 1; i <= 10; i++) {
            references.add("ref-" + i);
        }

        String formatted = (String) invoke(service, "formatReferenceList", references);

        assertTrue(formatted.startsWith("- ref-1\n- ref-2"));
        assertTrue(formatted.contains("- ref-8\n- ... 2 more"));
        assertEquals(9, formatted.split("\n").length);
    }

    @Test
    public void shouldRejectInvalidAiToolResponses() throws Exception {
        AIToolService service = new AIToolService();

        assertAiResponseError(service, "parseTitles", "title response is empty", "{\"titles\":[]}");
        assertAiResponseError(service, "parseTitles", "title response is invalid", "{\"title\":\"bad\"}");
        assertAiResponseError(service, "parseTags", "tags response is empty", "{\"tags\":[]}");
        assertAiResponseError(service, "parseTags", "tags response is invalid", "{\"tag\":\"bad\"}");
        assertAiResponseError(service, "parseArticleMarkdown", "markdown response is invalid",
                "{\"summary\":\"changed\"}");
        assertAiResponseError(service, "cleanAlias", "alias response is empty", "!!!");
        assertAiResponseError(service, "parseArticleScore", "score response is invalid",
                "{\"score\":80,\"summary\":\"\"}");
        assertAiResponseError(service, "parseArticleSeo", "SEO response is invalid",
                "{\"score\":80,\"summary\":\"SEO ok\",\"items\":[]}");
        assertAiResponseError(service, "parseArticleStructure", "structure response is invalid",
                "{\"summary\":\"ok\",\"items\":[]}");
        assertAiResponseError(service, "parseReaderQuestions", "reader questions response is invalid",
                "{\"summary\":\"ok\",\"items\":[]}");
        assertAiResponseError(service, "parseStringField", "alias invalid", "{}", "alias", "alias invalid");
    }

    private String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static void seedAiConfig(InMemoryZrLogDatabase db) throws Exception {
        db.putWebsite("ai_provider", "DEEP_SEEK");
        db.putWebsite("ai_model", "deepseek-chat");
        db.putWebsite("ai_api_key", "test-key");
        db.putWebsite("ai_prompt", "System prompt");
    }

    private static OptimizeWebsiteDescriptionRequest websiteDescriptionRequest() {
        OptimizeWebsiteDescriptionRequest request = new OptimizeWebsiteDescriptionRequest();
        request.setTitle("ZrLog");
        request.setSecond_title("Blog");
        request.setKeywords("java,blog");
        request.setDescription("Old description");
        request.setAuthor("admin");
        return request;
    }

    private static OptimizeAiPromptRequest aiPromptRequest() {
        OptimizeAiPromptRequest request = new OptimizeAiPromptRequest();
        request.setPrompt("Make the article assistant concise");
        return request;
    }

    private static GenerateArticleTitleRequest titleRequest() {
        GenerateArticleTitleRequest request = new GenerateArticleTitleRequest();
        request.setTitle("Draft title");
        request.setMarkdown("Draft markdown");
        request.setDigest("Draft digest");
        request.setKeywords("java,zrlog");
        request.setSelectedText("selected sentence");
        return request;
    }

    private static ScoreArticleRequest scoreRequest() {
        ScoreArticleRequest request = new ScoreArticleRequest();
        request.setTitle("Draft title");
        request.setMarkdown("Draft markdown");
        request.setDigest("Draft digest");
        request.setKeywords("java,zrlog");
        request.setSelectedText("score selected sentence");
        return request;
    }

    private GenerateArticleFieldRequest articleFieldRequest() {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setTitle("Draft title");
        request.setMarkdown(repeat("A long markdown body with ![cover](/cover.png) and https://zrlog.com . ", 4));
        request.setDigest("Draft digest");
        request.setKeywords("java,zrlog");
        request.setSelectedText("field selected sentence");
        request.setAlias("old-alias");
        request.setThumbnail("/cover.png");
        request.setTransparentPublish(true);
        request.setStaticSiteEnabled(true);
        request.setStaticSitePluginEnabled(true);
        return request;
    }

    private static String scoreJson(String summary) {
        return "{\"score\":88,\"summary\":\"" + summary + "\",\"items\":["
                + "{\"name\":\"SEO\",\"score\":80,\"suggestion\":\"Improve title\"}]}";
    }

    private static HttpResponse<String> completionResponse(String content) {
        return stringResponse(200, GSON.toJson(Map.of("choices",
                List.of(Map.of("message", Map.of("content", content))))));
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

    private static void assertProviderRequests(FakeHttpClient client, int expectedRequestCount) {
        assertEquals(expectedRequestCount, client.requests.size());
        assertEquals(expectedRequestCount, client.requestBodies.size());
        for (HttpRequest request : client.requests) {
            assertEquals("https://api.deepseek.com/chat/completions", request.uri().toString());
            assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElse(""));
            assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
            assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElse(""));
        }
    }

    private static String readBody(HttpRequest request) {
        return request.bodyPublisher().map(AIToolServiceTest::readBody).orElse("");
    }

    private static String readBody(HttpRequest.BodyPublisher publisher) {
        BodyCollector collector = new BodyCollector();
        publisher.subscribe(collector);
        return collector.body();
    }

    private static Object invoke(AIToolService service, String methodName, Object... args) throws Exception {
        Method method = findMethod(methodName, args.length);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private static void assertAiResponseError(AIToolService service, String methodName, String expectedMessage,
                                              Object... args)
            throws Exception {
        Throwable error = invokeFailure(service, methodName, args);
        assertTrue(error instanceof AIResponseException);
        assertTrue(error.getMessage().contains(expectedMessage));
    }

    private static Throwable invokeFailure(AIToolService service, String methodName, Object... args) throws Exception {
        try {
            invoke(service, methodName, args);
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
        throw new AssertionError("Expected method failure");
    }

    private static Method findMethod(String methodName, int parameterCount) {
        for (Method method : AIToolService.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + methodName);
    }

    private static class FakeHttpClient extends HttpClient {

        private final List<HttpResponse<String>> responses;
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> requestBodies = new ArrayList<>();
        private int index;

        private FakeHttpClient(HttpResponse<String>... responses) {
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
