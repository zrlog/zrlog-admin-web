package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonElement;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.dto.AIStreamPayloads;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest;
import com.zrlog.admin.business.rest.request.ScoreArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.I18nUtil;

import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AIChatService extends AIService {

    private static final int MAX_CONTINUATION_ROUNDS = 3;
    private static final Set<String> COMPLETE_FINISH_REASONS = Set.of("stop", "stop_sequence", "tool_calls", "function_call");
    private static final Set<String> CONTINUABLE_FINISH_REASONS = Set.of("length", "max_tokens",
            "max_output_tokens", "max_completion_tokens");

    public AIChatService() {
    }

    AIChatService(HttpClient client) {
        super(client);
    }

    public AIStreamResponse startStreamResponse(String input, Long articleId)
            throws IOException, InterruptedException, SQLException {
        return startStreamResponse(input, articleId, null, null, true);
    }

    public AIStreamResponse startStreamResponse(String input, Long articleId, String tool,
                                                GenerateArticleFieldRequest articleContext)
            throws IOException, InterruptedException, SQLException {
        return startStreamResponse(input, articleId, tool, articleContext, true);
    }

    public AIStreamResponse startStreamResponse(String input, Long articleId, String tool,
                                                GenerateArticleFieldRequest articleContext,
                                                boolean includeArticleContext)
            throws IOException, InterruptedException, SQLException {
        if (StringUtils.isNotEmpty(tool)) {
            return startToolStreamResponse(input, articleId, tool, articleContext);
        }
        AIWebSiteInfoWithAIMessages info = new WebSiteService().getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info);
        String requestBody = buildRequestBody(toProviderChatMessages(messages, includeArticleContext), info, true);

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            HttpRequest request = buildRequest(info, requestBody);
            HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String lastError = readErrorBody(response.body());
                if (response.statusCode() == 503 && i < maxRetries - 1) {
                    pauseBeforeStreamRetry(i);
                    continue;
                }
                return new AIStreamResponse(response.statusCode(), lastError, null);
            }

            PipedInputStream pin = new PipedInputStream();
            PipedOutputStream pout = new PipedOutputStream(pin);

            ThreadUtils.start(() -> {
                StringBuilder fullResponse = new StringBuilder();
                StringBuilder reasoningResponse = new StringBuilder();
                try {
                    StreamReadResult streamResult = readStreamResponse(response, pout, fullResponse, reasoningResponse,
                            info.isReasoningEnabled());
                    int continuationRounds = 0;
                    while (streamResult.isNeedContinuation() && continuationRounds < MAX_CONTINUATION_ROUNDS) {
                        continuationRounds++;
                        HttpResponse<InputStream> continuationResponse =
                                sendStreamRequestWithRetry(info, buildContinuationRequestBody(messages, info,
                                        fullResponse, includeArticleContext));
                        streamResult = readStreamResponse(continuationResponse, pout, fullResponse, reasoningResponse,
                                info.isReasoningEnabled());
                    }
                    if (streamResult.isNeedContinuation()) {
                        throw new AIIncompleteResponseException(streamResult.getFinishReason(), continuationRounds);
                    }
                    saveMessages(messages, articleId, fullResponse.toString(), reasoningResponse.toString(), info);
                } catch (Exception e) {
                    sendStreamError(pout, e, info, null);
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

    public List<AIResponseEntry.AIContentEntry> runToolResponse(String input, Long articleId, String tool,
                                                                GenerateArticleFieldRequest articleContext)
            throws SQLException, IOException, InterruptedException {
        if (articleContext == null) {
            throw new ArgsException("articleContext");
        }
        AIWebSiteInfoWithAIMessages info = new WebSiteService().getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info, tool);
        AIResponseEntry.AIContentEntry userMessage = messages.get(messages.size() - 1);
        ToolResult toolResult = runTool(tool, articleContext, buildToolConversationContext(tool, messages));
        AIResponseEntry.AIContentEntry savedMessage = saveToolMessage(messages, articleId, tool, toolResult, info);
        return List.of(userMessage, savedMessage);
    }

    private AIStreamResponse startToolStreamResponse(String input, Long articleId, String tool,
                                                     GenerateArticleFieldRequest articleContext)
            throws SQLException, IOException, InterruptedException {
        if (articleContext == null) {
            throw new ArgsException("articleContext");
        }
        AIWebSiteInfoWithAIMessages info = new WebSiteService().getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info, tool);
        try {
            ToolResult toolResult = runTool(tool, articleContext, buildToolConversationContext(tool, messages));
            AIResponseEntry.AIContentEntry savedMessage = saveToolMessage(messages, articleId, tool, toolResult, info);
            String payload = "data: " + gson.toJson(AIStreamPayloads.Chunk.tool(tool, toolResult.payload,
                    savedMessage.getMessageId())) + "\n\n"
                    + "data: " + gson.toJson(AIStreamPayloads.Chunk.content(toolResult.content)) + "\n\n";
            return new AIStreamResponse(200, "", new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return buildStreamErrorResponse(e, info, tool);
        }
    }

    private ToolResult runTool(String tool, GenerateArticleFieldRequest articleContext, String conversationContext)
            throws IOException, InterruptedException, SQLException {
        AIToolService toolService = new AIToolService();
        switch (tool) {
            case "title": {
                GenerateArticleTitleRequest request = toTitleRequest(articleContext);
                GenerateArticleTitleResponse response = toolService.generateArticleTitles(request, conversationContext);
                return new ToolResult(formatTitles(response), response);
            }
            case "alias": {
                GenerateArticleAliasResponse response = toolService.generateArticleAlias(articleContext, conversationContext);
                return new ToolResult("`" + response.getAlias() + "`", response);
            }
            case "digest": {
                GenerateArticleDigestResponse response = toolService.generateArticleDigest(articleContext, conversationContext);
                return new ToolResult(response.getDigest(), response);
            }
            case "tags": {
                GenerateArticleTagsResponse response = toolService.generateArticleTags(articleContext, conversationContext);
                return new ToolResult(formatTags(response), response);
            }
            case "rewrite": {
                GenerateArticleMarkdownResponse response =
                        toolService.rewriteArticleMarkdown(articleContext, conversationContext);
                return new ToolResult(formatMarkdownRewrite(response), response);
            }
            case "score": {
                ScoreArticleRequest request = toScoreRequest(articleContext);
                ScoreArticleResponse response = toolService.scoreArticle(request, conversationContext);
                return new ToolResult(formatScore(response), response);
            }
            case "publishCheck": {
                ScoreArticleResponse response = toolService.publishCheckArticle(articleContext, conversationContext);
                return new ToolResult(formatScore(response), response);
            }
            case "seo": {
                ArticleSeoCheckResponse response = toolService.checkArticleSeo(articleContext, conversationContext);
                return new ToolResult(formatSeo(response), response);
            }
            case "proofread": {
                ArticleProofreadResponse response = toolService.proofreadArticle(articleContext, conversationContext);
                return new ToolResult(formatProofread(response), response);
            }
            case "structure": {
                ArticleStructureAdviceResponse response =
                        toolService.adviseArticleStructure(articleContext, conversationContext);
                return new ToolResult(formatStructure(response), response);
            }
            case "questions": {
                ArticleReaderQuestionsResponse response =
                        toolService.generateReaderQuestions(articleContext, conversationContext);
                return new ToolResult(formatReaderQuestions(response), response);
            }
            case "cover": {
                com.zrlog.admin.business.rest.response.GenerateArticleCoverResponse response =
                        new AIImageService().generateArticleCover(articleContext);
                return new ToolResult("已生成文章封面", new AIStreamPayloads.CoverPayload(response.getUrl()));
            }
            default:
                throw new UnsupportedAIToolException(tool);
        }
    }

    private GenerateArticleTitleRequest toTitleRequest(GenerateArticleFieldRequest context) {
        GenerateArticleTitleRequest request = new GenerateArticleTitleRequest();
        request.setTitle(context.getTitle());
        request.setMarkdown(context.getMarkdown());
        request.setDigest(context.getDigest());
        request.setKeywords(context.getKeywords());
        request.setSelectedText(context.getSelectedText());
        return request;
    }

    private String buildToolConversationContext(String tool, List<AIResponseEntry.AIContentEntry> messages) {
        ToolContextPolicy policy = getToolContextPolicy(tool);
        if (policy == ToolContextPolicy.NONE) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, messages.size() - 8);
        for (int i = start; i < messages.size(); i++) {
            AIResponseEntry.AIContentEntry entry = messages.get(i);
            if (Objects.equals(entry.getRole(), "system") || StringUtils.isEmpty(entry.getContent())
                    || isArticleContextMessage(entry) || !shouldUseToolConversationEntry(policy, entry)) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append("Conversation context:\n");
            }
            sb.append(entry.getRole()).append(": ").append(truncateContext(entry.getContent())).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean shouldUseToolConversationEntry(ToolContextPolicy policy, AIResponseEntry.AIContentEntry entry) {
        if (policy == ToolContextPolicy.CHAT_ONLY) {
            return StringUtils.isEmpty(entry.getTool());
        }
        return true;
    }

    private boolean isArticleContextMessage(AIResponseEntry.AIContentEntry entry) {
        return Objects.equals(entry.getMessageType(), "articleContext");
    }

    /*
     * Tool context policy rules:
     * - FULL_CONVERSATION: use for text-generation tools that may refine previous assistant output.
     * - CHAT_ONLY: use for evaluation or artifact-generation tools. Keep ordinary user/assistant chat intent,
     *   but exclude tool outputs so scores, SEO checks, covers, and other payloads do not bias the next run.
     * - NONE: reserve for deterministic, strict-format, privacy-sensitive, or batch-isolated tools where any
     *   conversation history can reduce stability or leak irrelevant context.
     *
     * When adding a new article AI tool, classify it here first. Prefer CHAT_ONLY unless the tool clearly needs
     * previous tool results to iterate on text.
     */
    ToolContextPolicy getToolContextPolicy(String tool) {
        if (Objects.equals(tool, "publishCheck")
                || Objects.equals(tool, "score")
                || Objects.equals(tool, "seo")
                || Objects.equals(tool, "proofread")
                || Objects.equals(tool, "structure")
                || Objects.equals(tool, "questions")
                || Objects.equals(tool, "tags")
                || Objects.equals(tool, "cover")) {
            return ToolContextPolicy.CHAT_ONLY;
        }
        return ToolContextPolicy.FULL_CONVERSATION;
    }

    String truncateContext(String content) {
        if (content.length() <= 500) {
            return content;
        }
        return content.substring(0, 500);
    }

    private ScoreArticleRequest toScoreRequest(GenerateArticleFieldRequest context) {
        ScoreArticleRequest request = new ScoreArticleRequest();
        request.setTitle(context.getTitle());
        request.setMarkdown(context.getMarkdown());
        request.setDigest(context.getDigest());
        request.setKeywords(context.getKeywords());
        request.setSelectedText(context.getSelectedText());
        return request;
    }

    private String formatTitles(GenerateArticleTitleResponse response) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < response.getTitles().size(); i++) {
            sb.append(i + 1).append(". ").append(response.getTitles().get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatTags(GenerateArticleTagsResponse response) {
        return String.join(", ", response.getTags());
    }

    private String formatMarkdownRewrite(GenerateArticleMarkdownResponse response) {
        if (StringUtils.isNotEmpty(response.getSummary())) {
            return response.getSummary();
        }
        return response.getMarkdown();
    }

    private String formatScore(ScoreArticleResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(response.getScore()).append("\n\n");
        sb.append(response.getSummary()).append("\n\n");
        for (ScoreArticleResponse.ScoreItem item : response.getItems()) {
            sb.append("- ").append(item.getName()).append(" ").append(item.getScore()).append(": ")
                    .append(item.getSuggestion()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatSeo(ArticleSeoCheckResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("SEO: ").append(response.getScore()).append("\n\n");
        sb.append(response.getSummary()).append("\n\n");
        for (ArticleSeoCheckResponse.SeoItem item : response.getItems()) {
            sb.append("- ").append(item.getName()).append(" ").append(item.getStatus()).append(": ")
                    .append(item.getSuggestion()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatProofread(ArticleProofreadResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getSummary());
        for (ArticleProofreadResponse.ProofreadItem item : response.getItems()) {
            sb.append("\n- ").append(item.getOriginal()).append(": ").append(item.getIssue()).append(" -> ")
                    .append(item.getSuggestion());
        }
        return sb.toString().trim();
    }

    private String formatStructure(ArticleStructureAdviceResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getSummary());
        for (ArticleStructureAdviceResponse.StructureItem item : response.getItems()) {
            sb.append("\n- ").append(item.getName()).append(" ").append(item.getStatus()).append(": ")
                    .append(item.getSuggestion());
        }
        return sb.toString().trim();
    }

    private String formatReaderQuestions(ArticleReaderQuestionsResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getSummary());
        for (ArticleReaderQuestionsResponse.ReaderQuestionItem item : response.getItems()) {
            sb.append("\n- ").append(item.getQuestion()).append(": ").append(item.getSuggestion());
        }
        return sb.toString().trim();
    }

    private List<AIResponseEntry.AIContentEntry> prepareMessages(String input, AIWebSiteInfoWithAIMessages info) {
        return prepareMessages(input, info, null);
    }

    private List<AIResponseEntry.AIContentEntry> prepareMessages(String input, AIWebSiteInfoWithAIMessages info,
                                                                 String tool) {
        List<AIResponseEntry.AIContentEntry> messages = info.getAiMessages();
        new WebSiteService().ensureSystemMessage(messages, info.getAi_prompt());
        AIResponseEntry.AIContentEntry userEntry = new AIResponseEntry.AIContentEntry("user", input);
        if (StringUtils.isNotEmpty(tool)) {
            userEntry.setTool(tool);
        }
        messages.add(userEntry);
        return messages;
    }

    private HttpResponse<InputStream> sendStreamRequestWithRetry(AIWebSiteInfoWithAIMessages info, String requestBody)
            throws IOException, InterruptedException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            HttpRequest request = buildRequest(info, requestBody);
            HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                return response;
            }
            String lastError = readErrorBody(response.body());
            if (response.statusCode() == 503 && i < maxRetries - 1) {
                pauseBeforeStreamRetry(i);
                continue;
            }
            throw new AIRequestException(buildProviderErrorDetail(response.statusCode(), lastError));
        }
        throw new AIRequestException("Retry failed");
    }

    private String buildContinuationRequestBody(List<AIResponseEntry.AIContentEntry> messages,
                                                AIWebSiteInfoWithAIMessages info, StringBuilder fullResponse,
                                                boolean includeArticleContext) {
        List<AIResponseEntry.AIContentEntry> continuationMessages =
                new ArrayList<>(toProviderChatMessages(messages, includeArticleContext));
        continuationMessages.add(new AIResponseEntry.AIContentEntry("assistant", fullResponse.toString()));
        continuationMessages.add(new AIResponseEntry.AIContentEntry("user",
                "Continue exactly from where the previous response stopped. Do not repeat earlier content. "
                        + "Do not add a preface or summary."));
        return buildRequestBody(continuationMessages, info, true);
    }

    void pauseBeforeStreamRetry(int attempt) throws InterruptedException {
        Thread.sleep((long) Math.pow(2, attempt + 1) * 1000);
    }

    List<AIResponseEntry.AIContentEntry> toProviderChatMessages(List<AIResponseEntry.AIContentEntry> messages,
                                                                boolean includeArticleContext) {
        if (includeArticleContext) {
            return messages;
        }
        return messages.stream()
                .filter(message -> !isArticleContextMessage(message))
                .collect(java.util.stream.Collectors.toList());
    }

    private StreamReadResult readStreamResponse(HttpResponse<InputStream> response, OutputStream out,
                                                StringBuilder fullResponse) throws IOException {
        return readStreamResponse(response, out, fullResponse, new StringBuilder(), true);
    }

    private StreamReadResult readStreamResponse(HttpResponse<InputStream> response, OutputStream out,
                                                StringBuilder fullResponse, StringBuilder reasoningResponse,
                                                boolean reasoningEnabled) throws IOException {
        boolean streamCompleted = false;
        StreamReadResult streamResult = StreamReadResult.noFinishReason();
        try (InputStream aiStream = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(aiStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String dataLine = line.substring("data:".length()).trim();
                    if (Objects.equals(dataLine, "[DONE]")) {
                        streamCompleted = true;
                        continue;
                    }
                    if (StringUtils.isNotEmpty(dataLine)) {
                        StreamReadResult chunkResult = processChunk(dataLine, out, fullResponse, reasoningResponse,
                                reasoningEnabled);
                        if (chunkResult.hasFinishReason()) {
                            streamResult = chunkResult;
                            streamCompleted = true;
                        }
                    }
                }
            }
        }
        if (!streamCompleted) {
            throw new AIIncompleteResponseException(streamResult.getFinishReason());
        }
        return streamResult;
    }

    private StreamReadResult processChunk(String jsonData, OutputStream out, StringBuilder fullResponse)
            throws IOException {
        return processChunk(jsonData, out, fullResponse, new StringBuilder(), true);
    }

    private StreamReadResult processChunk(String jsonData, OutputStream out, StringBuilder fullResponse,
                                          StringBuilder reasoningResponse, boolean reasoningEnabled)
            throws IOException {
        try {
            AIProviderResponses.CompletionResponse chunk =
                    gson.fromJson(jsonData, AIProviderResponses.CompletionResponse.class);
            JsonElement error = chunk == null ? null : chunk.getError();
            if (error != null && !error.isJsonNull()) {
                throw new AIRequestException(toProviderStreamErrorDetail(error));
            }
            List<AIProviderResponses.Choice> choices = chunk == null ? null : chunk.getChoices();
            if (choices != null && !choices.isEmpty()) {
                AIProviderResponses.Choice choice = choices.get(0);
                String finishReason = getFinishReason(choice);
                AIProviderResponses.Delta delta = choice.getDelta();
                if (delta != null && delta.getContent() != null) {
                    String content = delta.getContent();
                    fullResponse.append(content);
                    String jsonChunk = gson.toJson(AIStreamPayloads.Chunk.content(content));
                    out.write(("data: " + jsonChunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                if (reasoningEnabled && delta != null) {
                    String reasoningContent = getReasoningContent(delta);
                    if (StringUtils.isNotEmpty(reasoningContent)) {
                        reasoningResponse.append(reasoningContent);
                        String jsonChunk = gson.toJson(AIStreamPayloads.Chunk.reasoning(reasoningContent));
                        out.write(("data: " + jsonChunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                }
                if (isIncompleteFinishReason(finishReason)) {
                    throw new AIIncompleteResponseException(finishReason);
                }
                if (StringUtils.isNotEmpty(finishReason)) {
                    return new StreamReadResult(finishReason, isContinuableFinishReason(finishReason));
                }
            }
            return StreamReadResult.noFinishReason();
        } catch (AIRequestException | AIResponseException | AIIncompleteResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new AIResponseException("stream chunk");
        }
    }

    private String getReasoningContent(AIProviderResponses.Delta delta) {
        Object reasoningContent = delta.getReasoningContent();
        if (reasoningContent == null) {
            reasoningContent = delta.getReasoning();
        }
        return reasoningContent instanceof String ? (String) reasoningContent : "";
    }

    private AIStreamResponse buildStreamErrorResponse(Exception e, AIWebSiteInfoWithAIMessages info, String tool) {
        String payload = "event: ai-error\n"
                + "data: " + gson.toJson(buildStreamErrorPayload(e, info, tool)) + "\n\n";
        return new AIStreamResponse(200, "", new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private void sendStreamError(OutputStream out, Exception e, AIWebSiteInfoWithAIMessages info, String tool) {
        try {
            out.write(("event: ai-error\n"
                    + "data: " + gson.toJson(buildStreamErrorPayload(e, info, tool)) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // Client connection may already be closed.
        }
    }

    AIStreamPayloads.ErrorPayload buildStreamErrorPayload(Exception e, AIWebSiteInfoWithAIMessages info) {
        return buildStreamErrorPayload(e, info, null);
    }

    AIStreamPayloads.ErrorPayload buildStreamErrorPayload(Exception e, AIWebSiteInfoWithAIMessages info, String tool) {
        AIStreamPayloads.ErrorPayload payload = new AIStreamPayloads.ErrorPayload();
        String message = StringUtils.isNotEmpty(e.getMessage())
                ? e.getMessage()
                : I18nUtil.getAdminBackendStringFromRes("admin.ai.error.request");
        payload.setMessage(message);
        payload.setErrorType(getStreamErrorType(e));
        if (e instanceof AIIncompleteResponseException) {
            AIIncompleteResponseException incomplete = (AIIncompleteResponseException) e;
            if (StringUtils.isNotEmpty(incomplete.getFinishReason())) {
                payload.setFinishReason(incomplete.getFinishReason());
            }
            if (incomplete.getContinuationRounds() != null) {
                payload.setContinuationRounds(incomplete.getContinuationRounds());
            }
        }
        if (info != null) {
            fillStreamErrorProvider(payload, info, tool);
        }
        return payload;
    }

    private void fillStreamErrorProvider(AIStreamPayloads.ErrorPayload payload, AIWebSiteInfoWithAIMessages info,
                                         String tool) {
        if (Objects.equals(tool, "cover")) {
            if (info.getAi_image_provider() != null) {
                payload.setProvider(info.getAi_image_provider().name());
            }
            if (StringUtils.isNotEmpty(info.getAi_image_model())) {
                payload.setModel(info.getAi_image_model());
            }
            return;
        }
        if (info.getAi_provider() != null) {
            payload.setProvider(info.getAi_provider().name());
        }
        if (StringUtils.isNotEmpty(info.getAi_model())) {
            payload.setModel(info.getAi_model());
        }
    }

    private String getStreamErrorType(Exception e) {
        if (e instanceof AIIncompleteResponseException) {
            return "incomplete_response";
        }
        if (e instanceof AIRequestException) {
            return "provider_request";
        }
        if (e instanceof AIResponseException) {
            return "provider_response";
        }
        if (e instanceof UnsupportedAIToolException) {
            return "unsupported_tool";
        }
        if (e instanceof UnsupportedAIImageGenerationException) {
            return "unsupported_image_generation";
        }
        if (e instanceof ArgsException) {
            return "configuration_required";
        }
        return "unknown";
    }

    private String getFinishReason(AIProviderResponses.Choice choice) {
        return choice.getFinishReason() == null ? "" : choice.getFinishReason();
    }

    private boolean isIncompleteFinishReason(String finishReason) {
        if (StringUtils.isEmpty(finishReason)) {
            return false;
        }
        String normalizedFinishReason = normalizeFinishReason(finishReason);
        return !COMPLETE_FINISH_REASONS.contains(normalizedFinishReason)
                && !CONTINUABLE_FINISH_REASONS.contains(normalizedFinishReason);
    }

    private boolean isContinuableFinishReason(String finishReason) {
        if (StringUtils.isEmpty(finishReason)) {
            return false;
        }
        return CONTINUABLE_FINISH_REASONS.contains(normalizeFinishReason(finishReason));
    }

    String normalizeFinishReason(String finishReason) {
        return finishReason.trim().toLowerCase(Locale.ROOT);
    }

    String toProviderStreamErrorDetail(JsonElement error) {
        return toProviderErrorDetail(error);
    }

    private static class StreamReadResult {

        private final String finishReason;
        private final boolean needContinuation;

        private StreamReadResult(String finishReason, boolean needContinuation) {
            this.finishReason = finishReason;
            this.needContinuation = needContinuation;
        }

        private static StreamReadResult noFinishReason() {
            return new StreamReadResult("", false);
        }

        private boolean hasFinishReason() {
            return StringUtils.isNotEmpty(finishReason);
        }

        private String getFinishReason() {
            return finishReason;
        }

        private boolean isNeedContinuation() {
            return needContinuation;
        }
    }

    String readErrorBody(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void saveMessages(List<AIResponseEntry.AIContentEntry> messages, Long articleId, String content,
                              String reasoningContent, AIWebSiteInfoWithAIMessages info) throws SQLException {
        AIResponseEntry.AIContentEntry entry = new AIResponseEntry.AIContentEntry("assistant", content);
        if (StringUtils.isNotEmpty(reasoningContent)) {
            entry.setReasoningContent(reasoningContent);
        }
        fillModelTrace(entry, info);
        messages.add(entry);
        if (!new WebSiteService().saveAIMessage(messages, articleId)) {
            throw new AIMessageSaveException();
        }
    }

    private AIResponseEntry.AIContentEntry saveToolMessage(List<AIResponseEntry.AIContentEntry> messages, Long articleId,
                                                           String tool, ToolResult toolResult,
                                                           AIWebSiteInfoWithAIMessages info) throws SQLException {
        AIResponseEntry.AIContentEntry entry = new AIResponseEntry.AIContentEntry("assistant", toolResult.content);
        entry.setTool(tool);
        entry.setPayload(toolResult.payload);
        fillModelTrace(entry, info);
        messages.add(entry);
        if (!new WebSiteService().saveAIMessage(messages, articleId)) {
            throw new AIMessageSaveException();
        }
        return entry;
    }

    private void fillModelTrace(AIResponseEntry.AIContentEntry entry, AIWebSiteInfoWithAIMessages info) {
        if (info.getAi_provider() != null) {
            entry.setProvider(info.getAi_provider().name());
        }
        entry.setModel(info.getAi_model());
    }

    private static class ToolResult {
        private final String content;
        private final Object payload;

        private ToolResult(String content, Object payload) {
            this.content = content;
            this.payload = payload;
        }
    }

    enum ToolContextPolicy {
        FULL_CONVERSATION,
        CHAT_ONLY,
        NONE
    }
}
