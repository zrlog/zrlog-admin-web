package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.AnalyzeCommentRequest;
import com.zrlog.admin.business.rest.response.AnalyzeCommentResponse;
import com.zrlog.admin.business.rest.response.AIResponseEntry;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Internal, advisory-only comment assistance; provider access stays in admin-ai. */
public class AICommentService extends AIService {
    public AnalyzeCommentResponse analyze(AnalyzeCommentRequest request) throws IOException, InterruptedException {
        request.doValid();
        return analyze(request, new AIConfigService().ai());
    }

    AnalyzeCommentResponse analyze(AnalyzeCommentRequest request, AIWebSiteInfo info) throws IOException, InterruptedException {
        request.doValid();
        checkAiConfig(info);
        // Keep this task bounded without changing the saved site model configuration.
        AIWebSiteInfo boundedInfo = gson.fromJson(gson.toJson(info), AIWebSiteInfo.class);
        Integer configuredLimit = info.getAi_max_completion_tokens();
        boundedInfo.setAi_max_completion_tokens(configuredLimit == null ? 2048 : Math.min(configuredLimit, 2048));
        JsonObject input = new JsonObject();
        input.addProperty("comment", request.getContent());
        String result = requestCompletion(boundedInfo, List.of(
                new AIResponseEntry.AIContentEntry("system", loadPromptResource(
                        "/ai/comment-review/prompt_", "/ai/comment-review/prompt_zh_CN.md")),
                new AIResponseEntry.AIContentEntry("user", input.toString())));
        return parse(result);
    }

    @Override protected HttpRequest buildRequest(AIWebSiteInfo info, String body) {
        HttpRequest original = super.buildRequest(info, body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(original.uri()).timeout(Duration.ofSeconds(25))
                .method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        original.headers().map().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.build();
    }

    AnalyzeCommentResponse parse(String text) {
        try {
            if (text == null || text.length() > 10000) throw new IllegalArgumentException();
            String json = text.trim();
            if (json.startsWith("```json") && json.endsWith("```")) {
                json = json.substring(7, json.length() - 3).trim();
            }
            JsonObject object = gson.fromJson(json, JsonObject.class);
            if (object == null || !stringField(object, "verdict") || !stringField(object, "reason") || !stringField(object, "reply")) {
                throw new IllegalArgumentException();
            }
            AnalyzeCommentResponse response = gson.fromJson(object, AnalyzeCommentResponse.class);
            response.setReason(response.getReason().trim());
            response.setReply(response.getReply().trim());
            if (!Set.of("normal", "spam", "review").contains(response.getVerdict())
                    || response.getReason().isEmpty() || response.getReason().length() > 300 || response.getReply().length() > 600) {
                throw new IllegalArgumentException();
            }
            if ("spam".equals(response.getVerdict())) response.setReply("");
            return response;
        } catch (RuntimeException exception) {
            throw new AIResponseException("Invalid comment review response");
        }
    }

    private boolean stringField(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive() && object.getAsJsonPrimitive(field).isString();
    }
}
