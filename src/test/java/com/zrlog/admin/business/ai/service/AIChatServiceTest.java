package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest;
import com.zrlog.admin.business.rest.request.ScoreArticleRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleProofreadResponse;
import com.zrlog.admin.business.rest.response.ArticleReaderQuestionsResponse;
import com.zrlog.admin.business.rest.response.ArticleSeoCheckResponse;
import com.zrlog.admin.business.rest.response.ArticleStructureAdviceResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleMarkdownResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleTagsResponse;
import com.zrlog.admin.business.rest.response.GenerateArticleTitleResponse;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AIChatServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    public void shouldKeepArticleContextWhenProviderChatIncludesSnapshot() {
        List<AIResponseEntry.AIContentEntry> messages = messagesWithArticleContext();

        List<AIResponseEntry.AIContentEntry> providerMessages =
                new AIChatService().toProviderChatMessages(messages, true);

        assertSame(messages, providerMessages);
        assertEquals(3, providerMessages.size());
    }

    @Test
    public void shouldExcludeArticleContextOnlyForProviderChatMessages() {
        List<AIResponseEntry.AIContentEntry> messages = messagesWithArticleContext();

        List<AIResponseEntry.AIContentEntry> providerMessages =
                new AIChatService().toProviderChatMessages(messages, false);

        assertEquals(2, providerMessages.size());
        assertEquals(3, messages.size());
        assertEquals("system", providerMessages.get(0).getRole());
        assertEquals("user", providerMessages.get(1).getRole());
    }

    @Test
    public void shouldBuildIncompleteStreamErrorPayload() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_model("gpt-test");
        Map<String, Object> payload = new AIChatService().buildStreamErrorPayload(
                new AIIncompleteResponseException("length", 3), info);

        assertEquals("incomplete_response", payload.get("errorType"));
        assertEquals("length", payload.get("finishReason"));
        assertEquals(3, payload.get("continuationRounds"));
        assertEquals("gpt-test", payload.get("model"));
    }

    @Test
    public void shouldClassifyProviderStreamErrors() {
        AIChatService service = new AIChatService();

        assertEquals("provider_request",
                service.buildStreamErrorPayload(new AIRequestException("429"), null).get("errorType"));
        assertEquals("provider_response",
                service.buildStreamErrorPayload(new AIResponseException("stream chunk"), null).get("errorType"));
    }

    @Test
    public void shouldClassifyUnsupportedToolStreamErrors() {
        AIChatService service = new AIChatService();

        assertEquals("unsupported_tool",
                service.buildStreamErrorPayload(new UnsupportedAIToolException("x"), null).get("errorType"));
        assertEquals("configuration_required",
                service.buildStreamErrorPayload(new ArgsException("ai_image_provider"), null).get("errorType"));
    }

    @Test
    public void shouldUseImageProviderForCoverStreamErrors() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        info.setAi_image_provider(AIProviderType.OPEN_AI);
        info.setAi_image_model("gpt-image-test");

        Map<String, Object> payload = new AIChatService().buildStreamErrorPayload(
                new UnsupportedAIImageGenerationException("model: gpt-image-test"), info, "cover");

        assertEquals("unsupported_image_generation", payload.get("errorType"));
        assertEquals("OPEN_AI", payload.get("provider"));
        assertEquals("gpt-image-test", payload.get("model"));
    }

    @Test
    public void shouldMapArticleContextToToolRequests() throws Exception {
        AIChatService service = new AIChatService();
        GenerateArticleFieldRequest context = articleContext();

        GenerateArticleTitleRequest titleRequest = (GenerateArticleTitleRequest) invoke(service, "toTitleRequest",
                context);
        ScoreArticleRequest scoreRequest = (ScoreArticleRequest) invoke(service, "toScoreRequest", context);

        assertEquals("Title", titleRequest.getTitle());
        assertEquals("Markdown", titleRequest.getMarkdown());
        assertEquals("Digest", titleRequest.getDigest());
        assertEquals("java,zrlog", titleRequest.getKeywords());
        assertEquals("Selected", titleRequest.getSelectedText());
        assertEquals("Title", scoreRequest.getTitle());
        assertEquals("Markdown", scoreRequest.getMarkdown());
        assertEquals("Digest", scoreRequest.getDigest());
        assertEquals("java,zrlog", scoreRequest.getKeywords());
        assertEquals("Selected", scoreRequest.getSelectedText());
    }

    @Test
    public void shouldBuildToolConversationContextByPolicy() throws Exception {
        AIChatService service = new AIChatService();
        AIResponseEntry.AIContentEntry system = new AIResponseEntry.AIContentEntry("system", "system");
        AIResponseEntry.AIContentEntry article = new AIResponseEntry.AIContentEntry("user", "article");
        article.setMessageType("articleContext");
        AIResponseEntry.AIContentEntry user = new AIResponseEntry.AIContentEntry("user", "question");
        AIResponseEntry.AIContentEntry tool = new AIResponseEntry.AIContentEntry("assistant", "score payload");
        tool.setTool("score");
        AIResponseEntry.AIContentEntry assistant = new AIResponseEntry.AIContentEntry("assistant", repeat("a", 550));
        List<AIResponseEntry.AIContentEntry> messages = List.of(system, article, user, tool, assistant);

        String full = (String) invoke(service, "buildToolConversationContext", "title", messages);

        assertTrue(full.startsWith("Conversation context:"));
        assertTrue(full.contains("user: question"));
        assertTrue(full.contains("assistant: score payload"));
        assertTrue(full.contains(repeat("a", 500)));
        assertFalse(full.contains("system"));
        assertFalse(full.contains("article"));
        for (String chatOnlyTool : List.of("publishCheck", "score", "seo", "proofread", "structure",
                "questions", "tags", "cover")) {
            String chatOnly = (String) invoke(service, "buildToolConversationContext", chatOnlyTool, messages);
            assertTrue(chatOnlyTool, chatOnly.contains("user: question"));
            assertFalse(chatOnlyTool, chatOnly.contains("score payload"));
        }
    }

    @Test
    public void shouldRejectMissingToolArticleContextBeforeLoadingWebsiteConfig() {
        AIChatService service = new AIChatService();

        assertThrows(ArgsException.class, () -> service.runToolResponse("input", 1L, "score", null));
        assertThrows(ArgsException.class, () -> service.startStreamResponse("input", 1L, "score", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldPrepareMessagesAndBuildContinuationRequestWithoutArticleContext() throws Exception {
        AIChatService service = new AIChatService();
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.OPEN_AI);
        info.setAi_model("gpt-test");
        info.setAiMessages(new ArrayList<>());

        List<AIResponseEntry.AIContentEntry> prepared =
                (List<AIResponseEntry.AIContentEntry>) invoke(service, "prepareMessages", "question", info, "score");
        AIResponseEntry.AIContentEntry articleContext = new AIResponseEntry.AIContentEntry("user", "article");
        articleContext.setMessageType("articleContext");
        prepared.add(1, articleContext);

        String requestBody = (String) invoke(service, "buildContinuationRequestBody", prepared, info,
                new StringBuilder("partial answer"), false);
        Map<String, Object> body = GSON.fromJson(requestBody, Map.class);
        List<Map<String, Object>> providerMessages = (List<Map<String, Object>>) body.get("messages");

        assertEquals(3, prepared.size());
        assertEquals("system", prepared.get(0).getRole());
        assertEquals("question", prepared.get(2).getContent());
        assertEquals("score", prepared.get(2).getTool());
        assertEquals(true, body.get("stream"));
        assertEquals("gpt-test", body.get("model"));
        assertEquals(4, providerMessages.size());
        assertFalse(providerMessages.stream().anyMatch(message -> "article".equals(message.get("content"))));
        assertEquals("assistant", providerMessages.get(2).get("role"));
        assertEquals("partial answer", providerMessages.get(2).get("content"));
        assertEquals("user", providerMessages.get(3).get("role"));
        assertTrue(providerMessages.get(3).get("content").toString().startsWith("Continue exactly"));
    }

    @Test
    public void shouldFormatToolResponsesForChatOutput() throws Exception {
        AIChatService service = new AIChatService();
        GenerateArticleTitleResponse titles = new GenerateArticleTitleResponse();
        titles.setTitles(List.of("One", "Two"));
        GenerateArticleTagsResponse tags = new GenerateArticleTagsResponse();
        tags.setTags(List.of("java", "zrlog"));
        GenerateArticleMarkdownResponse rewriteWithSummary = new GenerateArticleMarkdownResponse();
        rewriteWithSummary.setSummary("changed");
        rewriteWithSummary.setMarkdown("markdown");
        GenerateArticleMarkdownResponse rewriteWithoutSummary = new GenerateArticleMarkdownResponse();
        rewriteWithoutSummary.setMarkdown("markdown");
        ScoreArticleResponse score = scoreResponse();
        ArticleSeoCheckResponse seo = seoResponse();
        ArticleProofreadResponse proofread = proofreadResponse();
        ArticleStructureAdviceResponse structure = structureResponse();
        ArticleReaderQuestionsResponse questions = questionsResponse();

        assertEquals("1. One\n2. Two", invoke(service, "formatTitles", titles));
        assertEquals("java, zrlog", invoke(service, "formatTags", tags));
        assertEquals("changed", invoke(service, "formatMarkdownRewrite", rewriteWithSummary));
        assertEquals("markdown", invoke(service, "formatMarkdownRewrite", rewriteWithoutSummary));
        assertEquals("Score: 88\n\nLooks good\n\n- SEO 80: Improve title", invoke(service, "formatScore", score));
        assertEquals("SEO: 76\n\nSEO ok\n\n- title warning: shorten", invoke(service, "formatSeo", seo));
        assertEquals("Proofread ok\n- teh: typo -> the", invoke(service, "formatProofread", proofread));
        assertEquals("Structure ok\n- intro good: keep", invoke(service, "formatStructure", structure));
        assertEquals("Questions ok\n- Why?: answer it", invoke(service, "formatReaderQuestions", questions));
    }

    @Test
    public void shouldProcessProviderStreamChunks() throws Exception {
        AIChatService service = new AIChatService();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder fullResponse = new StringBuilder();
        ByteArrayOutputStream reasoningOut = new ByteArrayOutputStream();
        StringBuilder responseWithReasoning = new StringBuilder();
        StringBuilder reasoningResponse = new StringBuilder();

        Object stopResult = invoke(service, "processChunk",
                "{\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}",
                out, fullResponse);
        invoke(service, "processChunk",
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"think \",\"content\":\"answer\"},"
                        + "\"finish_reason\":\"stop\"}]}",
                reasoningOut, responseWithReasoning, reasoningResponse, true);
        invoke(service, "processChunk",
                "{\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\",\"content\":\"visible\"}}]}",
                reasoningOut, responseWithReasoning, reasoningResponse, false);
        Object lengthResult = invoke(service, "processChunk",
                "{\"choices\":[{\"finish_reason\":\"length\"}]}",
                new ByteArrayOutputStream(), new StringBuilder());
        Object camelCaseResult = invoke(service, "processChunk",
                "{\"choices\":[{\"finishReason\":\"stop_sequence\"}]}",
                new ByteArrayOutputStream(), new StringBuilder());
        Throwable incompleteFinishReason = invokeFailure(service, "processChunk",
                "{\"choices\":[{\"finish_reason\":\"content_filter\"}]}",
                new ByteArrayOutputStream(), new StringBuilder());

        assertEquals("hello", fullResponse.toString());
        assertEquals("data: {\"content\":\"hello\"}\n\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("answervisible", responseWithReasoning.toString());
        assertEquals("think ", reasoningResponse.toString());
        assertEquals("data: {\"content\":\"answer\"}\n\n"
                + "data: {\"reasoningContent\":\"think \"}\n\n"
                + "data: {\"content\":\"visible\"}\n\n", reasoningOut.toString(StandardCharsets.UTF_8));
        assertEquals("stop", invoke(stopResult, "getFinishReason"));
        assertFalse((Boolean) invoke(stopResult, "isNeedContinuation"));
        assertEquals("length", invoke(lengthResult, "getFinishReason"));
        assertTrue((Boolean) invoke(lengthResult, "isNeedContinuation"));
        assertEquals("stop_sequence", invoke(camelCaseResult, "getFinishReason"));
        assertFalse((Boolean) invoke(camelCaseResult, "isNeedContinuation"));
        assertTrue(incompleteFinishReason instanceof AIIncompleteResponseException);
    }

    @Test
    public void shouldReadProviderStreamAndForwardContent() throws Exception {
        AIChatService service = new AIChatService();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder fullResponse = new StringBuilder();

        Object result = invoke(service, "readStreamResponse", streamResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                        + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n"), out, fullResponse);

        assertEquals("hello", fullResponse.toString());
        assertEquals("data: {\"content\":\"hello\"}\n\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("stop", invoke(result, "getFinishReason"));
        assertFalse((Boolean) invoke(result, "isNeedContinuation"));
    }

    @Test
    public void shouldSurfaceProviderStreamErrorAndIncompleteStream() throws Exception {
        AIChatService service = new AIChatService();

        Throwable providerError = invokeFailure(service, "processChunk",
                "{\"error\":{\"message\":\"quota exceeded\"}}", new ByteArrayOutputStream(), new StringBuilder());
        Throwable providerStringError = invokeFailure(service, "processChunk",
                "{\"error\":\"plain error\"}", new ByteArrayOutputStream(), new StringBuilder());
        Throwable malformedChunk = invokeFailure(service, "processChunk",
                "{not json", new ByteArrayOutputStream(), new StringBuilder());
        Throwable incompleteStream = invokeFailure(service, "readStreamResponse", streamResponse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"),
                new ByteArrayOutputStream(), new StringBuilder());

        assertTrue(providerError instanceof AIRequestException);
        assertTrue(providerError.getMessage().contains("quota exceeded"));
        assertTrue(providerStringError instanceof AIRequestException);
        assertTrue(providerStringError.getMessage().contains("plain error"));
        assertTrue(malformedChunk instanceof AIResponseException);
        assertTrue(incompleteStream instanceof AIIncompleteResponseException);
    }

    @Test
    public void shouldRetryContinuationStreamRequestAndSurfaceProviderError() throws Exception {
        AIWebSiteInfoWithAIMessages info = providerInfo();
        FakeHttpClient client = new FakeHttpClient(
                streamResponse(503, "{\"error\":{\"message\":\"busy\"}}"),
                streamResponse(400, "{\"error\":{\"message\":\"bad request\"}}"));
        AIChatService service = new NoSleepAIChatService(client);

        Throwable error = invokeFailure(service, "sendStreamRequestWithRetry", info, "{}");

        assertTrue(error instanceof AIRequestException);
        assertTrue(error.getMessage().contains("status: 400"));
        assertTrue(error.getMessage().contains("bad request"));
        assertEquals(2, client.requests.size());
    }

    @Test
    public void shouldBuildAndSendStreamErrorEvents() throws Exception {
        AIChatService service = new AIChatService();
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        AIStreamResponse response = (AIStreamResponse) invoke(service, "buildStreamErrorResponse",
                new AIRequestException("quota"), info, null);
        invoke(service, "sendStreamError", out, new AIResponseException("bad chunk"), info, null);

        String responsePayload = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String sentPayload = out.toString(StandardCharsets.UTF_8);
        assertEquals(200, response.getStatusCode());
        assertEquals("", response.getErrorBody());
        assertTrue(responsePayload.startsWith("event: ai-error\n"));
        assertTrue(responsePayload.contains("\"errorType\":\"provider_request\""));
        assertTrue(responsePayload.contains("\"provider\":\"DEEP_SEEK\""));
        assertTrue(sentPayload.startsWith("event: ai-error\n"));
        assertTrue(sentPayload.contains("\"errorType\":\"provider_response\""));
    }

    @Test
    public void shouldIgnoreClosedClientWhenSendingStreamError() throws Exception {
        OutputStream closed = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("closed");
            }
        };

        invoke(new AIChatService(), "sendStreamError", closed, new AIResponseException("bad chunk"), null, null);
    }

    @Test
    public void shouldClassifyUnknownStreamErrorPayload() {
        Map<String, Object> payload = new AIChatService().buildStreamErrorPayload(new RuntimeException(), null);
        Map<String, Object> incomplete = new AIChatService().buildStreamErrorPayload(
                new AIIncompleteResponseException(""), null);

        assertEquals("unknown", payload.get("errorType"));
        assertTrue(payload.containsKey("message"));
        assertEquals("incomplete_response", incomplete.get("errorType"));
        assertFalse(incomplete.containsKey("finishReason"));
    }

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
            AIStreamResponse second = service.startStreamResponse("Question", 37L, null, null);
            String firstPayload = new String(first.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String secondPayload = new String(second.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(200, first.getStatusCode());
            assertEquals(200, second.getStatusCode());
            assertTrue(firstPayload.contains("first"));
            assertTrue(firstPayload.contains("\"reasoningContent\":\"plan \""));
            assertTrue(secondPayload.contains("second"));
            String firstStored = String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_36").get("value"));
            assertTrue(firstStored.contains("first"));
            assertTrue(firstStored.contains("\"reasoningContent\":\"plan \""));
            assertTrue(String.valueOf(db.queryOne(
                    "select value from website where name=?", "ai_chat_message_37").get("value")).contains("second"));
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
                    "select value from website where name=?", "ai_chat_message_39").get("value"));

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
                    "select value from website where name=?", "ai_chat_message_33").get("value"));

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
            assertTrue(payload.contains("event: ai-error"));
            assertTrue(payload.contains("\"errorType\":\"incomplete_response\""));
            assertTrue(payload.contains("\"continuationRounds\":3"));
            assertEquals(null, db.queryOne("select value from website where name=?", "ai_chat_message_38"));
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
    public void shouldReturnProviderErrorBodyWhenInitialStreamRequestFails() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedAiConfig(db);
            FakeHttpClient client = new FakeHttpClient(streamResponse(429, "{\"error\":{\"message\":\"quota\"}}"));
            AIChatService service = new NoSleepAIChatService(client);

            AIStreamResponse response = service.startStreamResponse("Retry", 35L, null, null, true);

            assertEquals(429, response.getStatusCode());
            assertTrue(response.getErrorBody().contains("quota"));
            assertEquals(null, response.getInputStream());
        }
    }

    private List<AIResponseEntry.AIContentEntry> messagesWithArticleContext() {
        AIResponseEntry.AIContentEntry system = new AIResponseEntry.AIContentEntry("system", "prompt");
        AIResponseEntry.AIContentEntry articleContext = new AIResponseEntry.AIContentEntry("user", "article");
        articleContext.setMessageType("articleContext");
        AIResponseEntry.AIContentEntry user = new AIResponseEntry.AIContentEntry("user", "question");
        return List.of(system, articleContext, user);
    }

    private static GenerateArticleFieldRequest articleContext() {
        GenerateArticleFieldRequest context = new GenerateArticleFieldRequest();
        context.setTitle("Title");
        context.setMarkdown("Markdown");
        context.setDigest("Digest");
        context.setKeywords("java,zrlog");
        context.setSelectedText("Selected");
        return context;
    }

    private static ScoreArticleResponse scoreResponse() {
        ScoreArticleResponse response = new ScoreArticleResponse();
        response.setScore(88);
        response.setSummary("Looks good");
        ScoreArticleResponse.ScoreItem item = new ScoreArticleResponse.ScoreItem();
        item.setName("SEO");
        item.setScore(80);
        item.setSuggestion("Improve title");
        response.setItems(List.of(item));
        return response;
    }

    private static ArticleSeoCheckResponse seoResponse() {
        ArticleSeoCheckResponse response = new ArticleSeoCheckResponse();
        response.setScore(76);
        response.setSummary("SEO ok");
        ArticleSeoCheckResponse.SeoItem item = new ArticleSeoCheckResponse.SeoItem();
        item.setName("title");
        item.setStatus("warning");
        item.setSuggestion("shorten");
        response.setItems(List.of(item));
        return response;
    }

    private static ArticleProofreadResponse proofreadResponse() {
        ArticleProofreadResponse response = new ArticleProofreadResponse();
        response.setSummary("Proofread ok");
        ArticleProofreadResponse.ProofreadItem item = new ArticleProofreadResponse.ProofreadItem();
        item.setOriginal("teh");
        item.setIssue("typo");
        item.setSuggestion("the");
        response.setItems(List.of(item));
        return response;
    }

    private static ArticleStructureAdviceResponse structureResponse() {
        ArticleStructureAdviceResponse response = new ArticleStructureAdviceResponse();
        response.setSummary("Structure ok");
        ArticleStructureAdviceResponse.StructureItem item = new ArticleStructureAdviceResponse.StructureItem();
        item.setName("intro");
        item.setStatus("good");
        item.setSuggestion("keep");
        response.setItems(List.of(item));
        return response;
    }

    private static ArticleReaderQuestionsResponse questionsResponse() {
        ArticleReaderQuestionsResponse response = new ArticleReaderQuestionsResponse();
        response.setSummary("Questions ok");
        ArticleReaderQuestionsResponse.ReaderQuestionItem item =
                new ArticleReaderQuestionsResponse.ReaderQuestionItem();
        item.setQuestion("Why?");
        item.setReason("reader intent");
        item.setSuggestion("answer it");
        response.setItems(List.of(item));
        return response;
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, args.length);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Throwable invokeFailure(Object target, String methodName, Object... args) throws Exception {
        try {
            invoke(target, methodName, args);
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
        throw new AssertionError("Expected method failure");
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalArgumentException("No method " + methodName);
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
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
                return HttpHeaders.of(Map.of(), (name, value) -> true);
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
