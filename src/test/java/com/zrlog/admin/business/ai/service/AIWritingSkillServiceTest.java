package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.dto.AIStreamPayloads;
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
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AIWritingSkillServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    public void shouldBuildIncompleteStreamErrorPayload() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_model("gpt-test");
        AIStreamPayloads.ErrorPayload payload = new AIWritingSkillService().buildStreamErrorPayload(
                new AIIncompleteResponseException("length", 3), info);

        assertEquals("incomplete_response", payload.getErrorType());
        assertEquals("length", payload.getFinishReason());
        assertEquals(Integer.valueOf(3), payload.getContinuationRounds());
        assertEquals("gpt-test", payload.getModel());
    }

    @Test
    public void shouldClassifyProviderStreamErrors() {
        AIWritingSkillService service = new AIWritingSkillService();

        assertEquals("provider_request",
                service.buildStreamErrorPayload(new AIRequestException("429"), null).getErrorType());
        assertEquals("provider_response",
                service.buildStreamErrorPayload(new AIResponseException("stream chunk"), null).getErrorType());
    }

    @Test
    public void shouldClassifyUnsupportedToolStreamErrors() {
        AIWritingSkillService service = new AIWritingSkillService();

        assertEquals("unsupported_tool",
                service.buildStreamErrorPayload(new UnsupportedAIToolException("x"), null).getErrorType());
        assertEquals("configuration_required",
                service.buildStreamErrorPayload(new ArgsException("ai_image_provider"), null).getErrorType());
    }

    @Test
    public void shouldUseImageProviderForCoverStreamErrors() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        info.setAi_image_provider(AIProviderType.OPEN_AI);
        info.setAi_image_model("gpt-image-test");

        AIStreamPayloads.ErrorPayload payload = new AIWritingSkillService().buildStreamErrorPayload(
                new UnsupportedAIImageGenerationException("model: gpt-image-test"), info, "cover");

        assertEquals("unsupported_image_generation", payload.getErrorType());
        assertEquals("OPEN_AI", payload.getProvider());
        assertEquals("gpt-image-test", payload.getModel());
    }

    @Test
    public void shouldMapArticleContextToToolRequests() throws Exception {
        AIWritingSkillService service = new AIWritingSkillService();
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
        AIWritingSkillService service = new AIWritingSkillService();
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
        AIWritingSkillService service = new AIWritingSkillService();

        assertThrows(ArgsException.class, () -> service.runToolResponse("input", 1L, "score", null));
        assertThrows(ArgsException.class, () -> service.startStreamResponse("input", 1L, "score", null));
    }

    @Test
    public void shouldFormatToolResponsesForChatOutput() throws Exception {
        AIWritingSkillService service = new AIWritingSkillService();
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
    public void shouldBuildAndSendStreamErrorEvents() throws Exception {
        AIWritingSkillService service = new AIWritingSkillService();
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

        invoke(new AIWritingSkillService(), "sendStreamError", closed, new AIResponseException("bad chunk"), null, null);
    }

    @Test
    public void shouldClassifyUnknownStreamErrorPayload() {
        AIStreamPayloads.ErrorPayload payload = new AIWritingSkillService().buildStreamErrorPayload(new RuntimeException(), null);
        AIStreamPayloads.ErrorPayload incomplete = new AIWritingSkillService().buildStreamErrorPayload(
                new AIIncompleteResponseException(""), null);

        assertEquals("unknown", payload.getErrorType());
        assertTrue(StringUtils.isNotEmpty(payload.getMessage()));
        assertEquals("incomplete_response", incomplete.getErrorType());
        assertEquals(null, incomplete.getFinishReason());
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

    private static AIWebSiteInfoWithAIMessages providerInfo() {
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();
        info.setAi_provider(AIProviderType.DEEP_SEEK);
        info.setAi_model("deepseek-chat");
        info.setAi_api_key("test-key");
        return info;
    }

}
