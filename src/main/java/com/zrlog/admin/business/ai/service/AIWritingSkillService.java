package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.dto.AIStreamPayloads;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleTitleRequest;
import com.zrlog.admin.business.rest.request.ScoreArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.util.I18nUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class AIWritingSkillService extends AIService {

    private final WebSiteService conversationStore;

    public AIWritingSkillService() { this(new WebSiteService().captureAccount()); }
    public AIWritingSkillService(WebSiteService conversationStore) { this.conversationStore = conversationStore; }

    public AIStreamResponse startStreamResponse(String input, Long articleId, String tool,
                                               GenerateArticleFieldRequest articleContext)
            throws IOException, InterruptedException, SQLException {
        return startToolStreamResponse(input, articleId, tool, articleContext);
    }

    public List<AIResponseEntry.AIContentEntry> runToolResponse(String input, Long articleId, String tool,
                                                                GenerateArticleFieldRequest articleContext)
            throws SQLException, IOException, InterruptedException {
        List<AIResponseEntry.AIContentEntry> generatedMessages =
                runToolResponseWithoutPersistence(input, articleId, tool, articleContext);
        if (!conversationStore.appendAIMessageEntries(generatedMessages, articleId)) {
            throw new AIMessageSaveException();
        }
        return generatedMessages;
    }

    public List<AIResponseEntry.AIContentEntry> runToolResponseWithoutPersistence(
            String input, Long articleId, String tool, GenerateArticleFieldRequest articleContext)
            throws SQLException, IOException, InterruptedException {
        if (articleContext == null) {
            throw new ArgsException("articleContext");
        }
        AIWebSiteInfoWithAIMessages info = conversationStore.getAiMessageInfoByArticleId(articleId);
        List<AIResponseEntry.AIContentEntry> messages = prepareMessages(input, info, tool);
        AIResponseEntry.AIContentEntry userMessage = messages.get(messages.size() - 1);
        ToolResult toolResult = runTool(tool, articleContext, buildToolConversationContext(tool, messages));
        return List.of(userMessage, buildToolMessage(tool, toolResult, info));
    }

    private AIStreamResponse startToolStreamResponse(String input, Long articleId, String tool,
                                                     GenerateArticleFieldRequest articleContext)
            throws SQLException, IOException, InterruptedException {
        if (articleContext == null) {
            throw new ArgsException("articleContext");
        }
        AIWebSiteInfoWithAIMessages info = conversationStore.getAiMessageInfoByArticleId(articleId);
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

    private AIResponseEntry.AIContentEntry saveToolMessage(List<AIResponseEntry.AIContentEntry> messages, Long articleId,
                                                           String tool, ToolResult toolResult,
                                                           AIWebSiteInfoWithAIMessages info) throws SQLException {
        AIResponseEntry.AIContentEntry entry = buildToolMessage(tool, toolResult, info);
        AIResponseEntry.AIContentEntry userMessage = messages.get(messages.size() - 1);
        if (!conversationStore.appendAIMessageEntries(List.of(userMessage, entry), articleId)) {
            throw new AIMessageSaveException();
        }
        return entry;
    }

    private AIResponseEntry.AIContentEntry buildToolMessage(String tool, ToolResult toolResult,
                                                            AIWebSiteInfoWithAIMessages info) {
        AIResponseEntry.AIContentEntry entry = new AIResponseEntry.AIContentEntry("assistant", toolResult.content);
        entry.setTool(tool);
        entry.setPayload(toolResult.payload);
        fillModelTrace(entry, info);
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
