package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class AIChatServiceTest {

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

    private List<AIResponseEntry.AIContentEntry> messagesWithArticleContext() {
        AIResponseEntry.AIContentEntry system = new AIResponseEntry.AIContentEntry("system", "prompt");
        AIResponseEntry.AIContentEntry articleContext = new AIResponseEntry.AIContentEntry("user", "article");
        articleContext.setMessageType("articleContext");
        AIResponseEntry.AIContentEntry user = new AIResponseEntry.AIContentEntry("user", "question");
        return List.of(system, articleContext, user);
    }
}
