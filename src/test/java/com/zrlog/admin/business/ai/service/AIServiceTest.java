package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.net.http.HttpRequest;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class AIServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    public void shouldBuildProviderRequestBodyWithTokenParameterByProvider() {
        TestAIService service = new TestAIService();
        AIWebSiteInfo openAi = info(AIProviderType.OPEN_AI);
        openAi.setAi_max_completion_tokens(512);
        AIWebSiteInfo qwen = info(AIProviderType.QWEN);
        qwen.setAi_max_completion_tokens(256);
        List<AIResponseEntry.AIContentEntry> messages = List.of(message("system", "prompt"), message("user", "hi"));

        JsonObject openAiBody = GSON.fromJson(service.body(messages, openAi, true), JsonObject.class);
        JsonObject qwenBody = GSON.fromJson(service.body(messages, qwen, false), JsonObject.class);
        AIWebSiteInfo qwenWithoutReasoning = info(AIProviderType.QWEN);
        qwenWithoutReasoning.setAi_reasoning_enabled(false);
        JsonObject qwenWithoutReasoningBody =
                GSON.fromJson(service.body(messages, qwenWithoutReasoning, false), JsonObject.class);

        assertEquals("gpt-test", openAiBody.get("model").getAsString());
        assertEquals(true, openAiBody.get("stream").getAsBoolean());
        assertEquals(512, openAiBody.get("max_completion_tokens").getAsInt());
        assertFalse(openAiBody.has("max_tokens"));
        assertFalse(openAiBody.has("enable_thinking"));
        assertEquals("qwen-test", qwenBody.get("model").getAsString());
        assertEquals(false, qwenBody.get("stream").getAsBoolean());
        assertEquals(256, qwenBody.get("max_tokens").getAsInt());
        assertEquals(true, qwenBody.get("enable_thinking").getAsBoolean());
        assertEquals(false, qwenWithoutReasoningBody.get("enable_thinking").getAsBoolean());
        JsonArray providerMessages = openAiBody.getAsJsonArray("messages");
        assertEquals(2, providerMessages.size());
        JsonObject firstMessage = providerMessages.get(0).getAsJsonObject();
        assertEquals("system", firstMessage.get("role").getAsString());
        assertEquals("prompt", firstMessage.get("content").getAsString());
    }

    @Test
    public void shouldBuildProviderRequestHeaders() {
        TestAIService service = new TestAIService();
        AIWebSiteInfo info = info(AIProviderType.DEEP_SEEK);

        HttpRequest request = service.request(info, "{}");

        assertEquals(AIProviderType.DEEP_SEEK.getBaseUrl() + "/chat/completions", request.uri().toString());
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
        assertEquals("Bearer key", request.headers().firstValue("Authorization").orElse(""));
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElse(""));

        info.setAi_base_url("http://localhost:11434/v1");
        HttpRequest customRequest = service.request(info, "{}");
        assertEquals("http://localhost:11434/v1/chat/completions", customRequest.uri().toString());

        info.setAi_base_url("https://gateway.example.com/openai/chat/completions");
        HttpRequest compatibleFullUrlRequest = service.request(info, "{}");
        assertEquals("https://gateway.example.com/openai/chat/completions",
                compatibleFullUrlRequest.uri().toString());

        info.setAi_base_url("http://localhost:11434/v1");
        info.setAi_api_key("");
        HttpRequest requestWithoutKey = service.request(info, "{}");
        assertFalse(requestWithoutKey.headers().firstValue("Authorization").isPresent());
    }

    @Test
    public void shouldValidateAndNormalizeCustomServiceUrl() {
        AIWebSiteInfo valid = info(AIProviderType.OPEN_AI);
        valid.setAi_model(" local-model:latest ");
        valid.setAi_base_url(" https://gateway.example.com/v1/?api-version=1 ");

        valid.doValid();

        assertEquals("local-model:latest", valid.getAi_model());
        assertEquals("https://gateway.example.com/v1?api-version=1", valid.getAi_base_url());
        for (String invalidUrl : List.of("ftp://example.com/chat", "/v1/chat/completions",
                "https://user:pass@example.com/chat", "https://example.com/chat#fragment")) {
            AIWebSiteInfo invalid = info(AIProviderType.OPEN_AI);
            invalid.setAi_base_url(invalidUrl);
            assertThrows(ArgsException.class, invalid::doValid);
        }
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
        AIWebSiteInfo blankModel = info(AIProviderType.OPEN_AI);
        blankModel.setAi_model("   ");
        assertThrows(ArgsException.class, blankModel::doValid);
        AIWebSiteInfo missingKey = info(AIProviderType.OPEN_AI);
        missingKey.setAi_api_key("");
        assertThrows(ArgsException.class, () -> service.check(missingKey));
        missingKey.setAi_base_url("http://localhost:11434/v1");
        service.check(missingKey);
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
