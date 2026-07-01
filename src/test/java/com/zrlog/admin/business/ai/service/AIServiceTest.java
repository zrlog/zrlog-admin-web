package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class AIServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    @SuppressWarnings("unchecked")
    public void shouldBuildProviderRequestBodyWithTokenParameterByProvider() {
        TestAIService service = new TestAIService();
        AIWebSiteInfo openAi = info(AIProviderType.OPEN_AI);
        openAi.setAi_max_completion_tokens(512);
        AIWebSiteInfo qwen = info(AIProviderType.QWEN);
        qwen.setAi_max_completion_tokens(256);
        List<AIResponseEntry.AIContentEntry> messages = List.of(message("system", "prompt"), message("user", "hi"));

        Map<String, Object> openAiBody = GSON.fromJson(service.body(messages, openAi, true), Map.class);
        Map<String, Object> qwenBody = GSON.fromJson(service.body(messages, qwen, false), Map.class);
        AIWebSiteInfo qwenWithoutReasoning = info(AIProviderType.QWEN);
        qwenWithoutReasoning.setAi_reasoning_enabled(false);
        Map<String, Object> qwenWithoutReasoningBody =
                GSON.fromJson(service.body(messages, qwenWithoutReasoning, false), Map.class);

        assertEquals("gpt-test", openAiBody.get("model"));
        assertEquals(true, openAiBody.get("stream"));
        assertEquals(512.0, openAiBody.get("max_completion_tokens"));
        assertFalse(openAiBody.containsKey("max_tokens"));
        assertFalse(openAiBody.containsKey("enable_thinking"));
        assertEquals("qwen-test", qwenBody.get("model"));
        assertEquals(false, qwenBody.get("stream"));
        assertEquals(256.0, qwenBody.get("max_tokens"));
        assertEquals(true, qwenBody.get("enable_thinking"));
        assertEquals(false, qwenWithoutReasoningBody.get("enable_thinking"));
        List<Map<String, Object>> providerMessages = (List<Map<String, Object>>) openAiBody.get("messages");
        assertEquals(2, providerMessages.size());
        assertEquals("system", providerMessages.get(0).get("role"));
        assertEquals("prompt", providerMessages.get(0).get("content"));
    }

    @Test
    public void shouldBuildProviderRequestHeaders() {
        TestAIService service = new TestAIService();
        AIWebSiteInfo info = info(AIProviderType.DEEP_SEEK);

        HttpRequest request = service.request(info, "{}");

        assertEquals(AIProviderType.DEEP_SEEK.getBaseUrl(), request.uri().toString());
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("Bearer key", request.headers().firstValue("Authorization").orElse(""));
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElse(""));
    }

    @Test
    public void shouldParseProviderErrorDetails() {
        TestAIService service = new TestAIService();

        assertEquals("status: 429, message: quota",
                service.error(429, "{\"error\":{\"message\":\"quota\"}}"));
        assertEquals("status: 500, message: unavailable",
                service.error(500, "{\"error\":\"unavailable\"}"));
        assertEquals("status: 400, message: bad request",
                service.error(400, "{\"message\":\"bad request\"}"));
        assertEquals("status: 502, message: raw body", service.error(502, " raw body "));
        assertEquals("status: 503", service.error(503, ""));
        assertEquals(522, service.error(500, repeat("a", 600)).length());
    }

    @Test
    public void shouldValidateAiConfigAndPromptHelpers() {
        TestAIService service = new TestAIService();
        AIWebSiteInfo valid = info(AIProviderType.OPEN_AI);

        service.check(valid);
        assertThrows(ArgsException.class, () -> service.check(new AIWebSiteInfo()));
        AIWebSiteInfo missingModel = info(AIProviderType.OPEN_AI);
        missingModel.setAi_model("");
        assertThrows(ArgsException.class, () -> service.check(missingModel));
        AIWebSiteInfo missingKey = info(AIProviderType.OPEN_AI);
        missingKey.setAi_api_key("");
        assertThrows(ArgsException.class, () -> service.check(missingKey));
        assertEquals("", service.blank(null));
        assertEquals("value", service.blank("value"));
        assertEquals("prompt", service.selected("prompt", ""));
        assertEquals("prompt\n\nSelected text from the current editor selection:\ntext",
                service.selected("prompt", " text "));
        assertThrows(AIPromptResourceException.class,
                () -> service.prompt("/missing/prefix-", "/missing/fallback.md"));
    }

    private static AIWebSiteInfo info(AIProviderType provider) {
        AIWebSiteInfo info = new AIWebSiteInfo();
        info.setAi_provider(provider);
        info.setAi_model(provider == AIProviderType.QWEN ? "qwen-test" : "gpt-test");
        info.setAi_api_key("key");
        return info;
    }

    private static AIResponseEntry.AIContentEntry message(String role, String content) {
        return new AIResponseEntry.AIContentEntry(role, content);
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static class TestAIService extends AIService {

        private String body(List<AIResponseEntry.AIContentEntry> messages, AIWebSiteInfo info, boolean stream) {
            return buildRequestBody(messages, info, stream);
        }

        private HttpRequest request(AIWebSiteInfo info, String body) {
            return buildRequest(info, body);
        }

        private String error(int statusCode, String body) {
            return buildProviderErrorDetail(statusCode, body);
        }

        private void check(AIWebSiteInfo info) {
            checkAiConfig(info);
        }

        private String blank(String value) {
            return emptyToBlank(value);
        }

        private String selected(String prompt, String selectedText) {
            return appendSelectedTextContext(prompt, selectedText);
        }

        private String prompt(String resourcePrefix, String fallbackResource) {
            return loadPromptResource(resourcePrefix, fallbackResource);
        }
    }
}
