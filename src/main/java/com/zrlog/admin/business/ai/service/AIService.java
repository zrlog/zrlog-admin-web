package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AIService {

    private final HttpClient client;

    protected final Gson gson = new Gson();

    public AIService() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    protected AIService(HttpClient client) {
        this.client = client;
    }

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
        AIProviderResponses.CompletionResponse responseBody =
                gson.fromJson(response.body(), AIProviderResponses.CompletionResponse.class);
        List<AIProviderResponses.Choice> choices = responseBody == null ? null : responseBody.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw new AIResponseException("choices is empty");
        }
        AIProviderResponses.Message message = choices.get(0).getMessage();
        if (message == null || message.getContent() == null) {
            throw new AIResponseException("content is empty");
        }
        return message.getContent();
    }

    protected String buildRequestBody(List<AIResponseEntry.AIContentEntry> messages, AIWebSiteInfo info, boolean stream) {
        AIProviderRequests.CompletionRequest request = new AIProviderRequests.CompletionRequest();
        request.setMessages(toProviderMessages(messages));
        request.setModel(info.getAi_model());
        request.setStream(stream);
        if (Objects.nonNull(info.getAi_max_completion_tokens()) && info.getAi_max_completion_tokens() > 0) {
            applyMaxCompletionTokens(request, info);
        }
        applyReasoningRequestParams(request, info);
        return gson.toJson(request);
    }

    private void applyMaxCompletionTokens(AIProviderRequests.CompletionRequest request, AIWebSiteInfo info) {
        if (Objects.equals(info.getAi_provider(), AIProviderType.OPEN_AI)
                || Objects.equals(info.getAi_provider(), AIProviderType.GOOGLE_GEMINI)) {
            request.setMaxCompletionTokens(info.getAi_max_completion_tokens());
            return;
        }
        request.setMaxTokens(info.getAi_max_completion_tokens());
    }

    private void applyReasoningRequestParams(AIProviderRequests.CompletionRequest request, AIWebSiteInfo info) {
        if (Objects.equals(info.getAi_provider(), AIProviderType.QWEN)) {
            request.setEnableThinking(info.isReasoningEnabled());
        }
    }

    private List<AIProviderRequests.Message> toProviderMessages(List<AIResponseEntry.AIContentEntry> messages) {
        return messages.stream()
                .map(message -> new AIProviderRequests.Message(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    protected HttpRequest buildRequest(AIWebSiteInfo info, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveRequestUrl(info)))
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (StringUtils.isNotEmpty(info.getAi_api_key())) {
            builder.header("Authorization", "Bearer " + info.getAi_api_key());
        }
        return builder.build();
    }

    private String resolveRequestUrl(AIWebSiteInfo info) {
        return resolveEndpointUrl(info.getAi_base_url(), info.getAi_provider().getBaseUrl(), "/chat/completions");
    }

    protected static String resolveEndpointUrl(String configuredBaseUrl, String defaultBaseUrl, String endpointPath) {
        String baseUrl = StringUtils.isEmpty(configuredBaseUrl) ? defaultBaseUrl : configuredBaseUrl;
        int queryIndex = baseUrl.indexOf('?');
        String query = queryIndex >= 0 ? baseUrl.substring(queryIndex) : "";
        String path = queryIndex >= 0 ? baseUrl.substring(0, queryIndex) : baseUrl;
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith(endpointPath)) {
            path += endpointPath;
        }
        return path + query;
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
            AIProviderResponses.CompletionResponse providerResponse =
                    gson.fromJson(body, AIProviderResponses.CompletionResponse.class);
            String errorMessage = toProviderErrorDetail(providerResponse == null ? null : providerResponse.getError());
            if (StringUtils.isNotEmpty(errorMessage)) {
                return errorMessage;
            }
            Object message = providerResponse == null ? null : providerResponse.getMessage();
            if (message != null && StringUtils.isNotEmpty(message.toString())) {
                return message.toString();
            }
        } catch (Exception ignored) {
        }
        return truncateProviderErrorBody(body);
    }

    protected String toProviderErrorDetail(JsonElement error) {
        if (error == null || error.isJsonNull()) {
            return "";
        }
        if (error.isJsonObject()) {
            AIProviderResponses.ErrorPayload payload = gson.fromJson(error, AIProviderResponses.ErrorPayload.class);
            if (payload != null && StringUtils.isNotEmpty(payload.getMessage())) {
                return payload.getMessage();
            }
        }
        if (error.isJsonPrimitive()) {
            return error.getAsString();
        }
        return error.toString();
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
        if (StringUtils.isEmpty(info.getAi_api_key()) && StringUtils.isEmpty(info.getAi_base_url())) {
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
