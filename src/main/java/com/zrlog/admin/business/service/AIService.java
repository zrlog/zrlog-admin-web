package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.dto.AIStreamResponse;
import com.zrlog.admin.business.exception.AdminUnknownException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.util.ThreadUtils;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIService {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Gson gson = new Gson();

    /**
     * 全量获取响应（非流式，主要用于 Lambda 或普通接口）
     */
    public List<AIResponseEntry.AIContentEntry> getResponse(String input, Long articleId)
            throws IOException, InterruptedException, SQLException {
        AIWebSiteInfoWithAIMessages info = new WebSiteService().getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info);
        String requestBody = buildRequestBody(messages, info.getAi_model(), false);

        HttpRequest request = buildRequest(info, requestBody);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new AdminUnknownException("AI request failed, status: " + response.statusCode() + ", body: " + response.body());
        }

        Map responseMap = gson.fromJson(response.body(), Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) messageMap.get("content");

        saveMessages(messages, articleId, content);
        return List.of(new AIResponseEntry.AIContentEntry("assistant", content));
    }

    /**
     * 启动流式响应
     */
    /**
     * 启动流式响应并等待连接建立
     */
    public AIStreamResponse startStreamResponse(String input, Long articleId) throws IOException, InterruptedException, SQLException {
        AIWebSiteInfoWithAIMessages info = new WebSiteService().getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info);
        String requestBody = buildRequestBody(messages, info.getAi_model(), true);

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            HttpRequest request = buildRequest(info, requestBody);
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String lastError = readErrorBody(response.body());
                if (response.statusCode() == 503 && i < maxRetries - 1) {
                    Thread.sleep((long) Math.pow(2, i + 1) * 1000);
                    continue;
                }
                return new AIStreamResponse(response.statusCode(), lastError, null);
            }

            // 连接成功，创建管道并在后台消费
            PipedInputStream pin = new PipedInputStream();
            PipedOutputStream pout = new PipedOutputStream(pin);

            ThreadUtils.start(() -> {
                StringBuilder fullResponse = new StringBuilder();
                try (InputStream aiStream = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(aiStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                            processChunk(line.substring(6), pout, fullResponse);
                        }
                    }
                    saveMessages(messages, articleId, fullResponse.toString());
                } catch (Exception e) {
                    // Log error
                } finally {
                    try {
                        pout.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            return new AIStreamResponse(200, "", pin);
        }
        return new AIStreamResponse(500, "Retry failed", null);
    }


    private List<AIResponseEntry.AIContentEntry> prepareMessages(String input, AIWebSiteInfoWithAIMessages info) {
        List<AIResponseEntry.AIContentEntry> messages = info.getAiMessages();
        if (messages.isEmpty()) {
            messages.add(new AIResponseEntry.AIContentEntry("system", info.getAi_prompt()));
        }
        messages.add(new AIResponseEntry.AIContentEntry("user", input));
        return messages;
    }

    private String buildRequestBody(List<AIResponseEntry.AIContentEntry> messages, String model, boolean stream) {
        Map<String, Object> params = new HashMap<>();
        params.put("messages", messages);
        params.put("model", model);
        params.put("stream", stream);
        return gson.toJson(params);
    }

    private HttpRequest buildRequest(AIWebSiteInfoWithAIMessages info, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(info.getAi_provider().getBaseUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + info.getAi_api_key())
                .header("Accept-Encoding", "identity")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private void processChunk(String jsonData, OutputStream out, StringBuilder fullResponse) throws IOException {
        try {
            Map map = gson.fromJson(jsonData, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta != null && delta.get("content") != null) {
                    String content = (String) delta.get("content");
                    fullResponse.append(content);
                    String jsonChunk = gson.toJson(Map.of("content", content));
                    out.write(("data: " + jsonChunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String readErrorBody(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private void saveMessages(List<AIResponseEntry.AIContentEntry> messages, Long articleId, String content) throws SQLException {
        messages.add(new AIResponseEntry.AIContentEntry("assistant", content));
        if (!new WebSiteService().saveAIMessage(messages, articleId)) {
            throw new AdminUnknownException("Save to db failed");
        }
    }
}

