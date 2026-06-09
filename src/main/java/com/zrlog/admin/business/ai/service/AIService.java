package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AIService {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    protected final Gson gson = new Gson();

    protected HttpClient client() {
        return client;
    }

    protected String requestCompletion(AIWebSiteInfo info, List<AIResponseEntry.AIContentEntry> messages)
            throws IOException, InterruptedException {
        String requestBody = buildRequestBody(messages, info, false);
        HttpRequest request = buildRequest(info, requestBody);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIRequestException(buildProviderErrorDetail(response.statusCode(), response.body()));
        }
        Map responseMap = gson.fromJson(response.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AIResponseException("choices is empty");
        }
        Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");
        if (messageMap == null || messageMap.get("content") == null) {
            throw new AIResponseException("content is empty");
        }
        return (String) messageMap.get("content");
    }

    protected String buildRequestBody(List<AIResponseEntry.AIContentEntry> messages, AIWebSiteInfo info, boolean stream) {
        Map<String, Object> params = new HashMap<>();
        params.put("messages", toProviderMessages(messages));
        params.put("model", info.getAi_model());
        params.put("stream", stream);
        if (Objects.nonNull(info.getAi_max_completion_tokens()) && info.getAi_max_completion_tokens() > 0) {
            params.put(getMaxCompletionTokensParameter(info.getAi_provider()), info.getAi_max_completion_tokens());
        }
        return gson.toJson(params);
    }

    private String getMaxCompletionTokensParameter(AIProviderType provider) {
        if (Objects.equals(provider, AIProviderType.OPEN_AI) || Objects.equals(provider, AIProviderType.GOOGLE_GEMINI)) {
            return "max_completion_tokens";
        }
        return "max_tokens";
    }

    private List<AIResponseEntry.AIContentEntry> toProviderMessages(List<AIResponseEntry.AIContentEntry> messages) {
        return messages.stream()
                .map(message -> new AIResponseEntry.AIContentEntry(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    protected HttpRequest buildRequest(AIWebSiteInfo info, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(info.getAi_provider().getBaseUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + info.getAi_api_key())
                .header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    protected String buildProviderErrorDetail(int statusCode, String body) {
        String errorMessage = parseProviderErrorMessage(body);
        if (StringUtils.isNotEmpty(errorMessage)) {
            return "status: " + statusCode + ", message: " + errorMessage;
        }
        return "status: " + statusCode;
    }

    private String parseProviderErrorMessage(String body) {
        if (StringUtils.isEmpty(body)) {
            return "";
        }
        try {
            Map responseMap = gson.fromJson(body, Map.class);
            Object error = responseMap.get("error");
            if (error instanceof Map) {
                Object message = ((Map<?, ?>) error).get("message");
                if (message != null && StringUtils.isNotEmpty(message.toString())) {
                    return message.toString();
                }
            }
            if (error != null && StringUtils.isNotEmpty(error.toString())) {
                return error.toString();
            }
            Object message = responseMap.get("message");
            if (message != null && StringUtils.isNotEmpty(message.toString())) {
                return message.toString();
            }
        } catch (Exception ignored) {
        }
        return truncateProviderErrorBody(body);
    }

    private String truncateProviderErrorBody(String body) {
        String cleaned = body.trim();
        if (cleaned.length() <= 500) {
            return cleaned;
        }
        return cleaned.substring(0, 500);
    }

    protected void checkAiConfig(AIWebSiteInfo info) {
        if (info.getAi_provider() == null) {
            throw new ArgsException("ai_provider");
        }
        if (StringUtils.isEmpty(info.getAi_model())) {
            throw new ArgsException("ai_model");
        }
        if (StringUtils.isEmpty(info.getAi_api_key())) {
            throw new ArgsException("ai_api_key");
        }
    }

    protected String loadPromptResource(String resourcePrefix, String fallbackResource) {
        String promptPath = resourcePrefix + I18nUtil.getCurrentLocale() + ".md";
        InputStream promptInputStream = AIService.class.getResourceAsStream(promptPath);
        if (promptInputStream == null) {
            promptInputStream = AIService.class.getResourceAsStream(fallbackResource);
        }
        if (promptInputStream == null) {
            throw new AIPromptResourceException(promptPath);
        }
        return IOUtil.getStringInputStream(promptInputStream);
    }

    protected String emptyToBlank(String value) {
        return StringUtils.isNotEmpty(value) ? value : "";
    }

    protected String appendSelectedTextContext(String prompt, String selectedText) {
        String content = emptyToBlank(selectedText).trim();
        if (StringUtils.isEmpty(content)) {
            return prompt;
        }
        return prompt + "\n\nSelected text from the current editor selection:\n" + content;
    }
}
