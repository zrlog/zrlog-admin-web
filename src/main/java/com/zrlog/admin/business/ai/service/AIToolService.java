package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.prompt.AIPromptVO;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.common.exception.ArgsException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIToolService extends AIService {

    private static final int REWRITE_MIN_MARKDOWN_LENGTH = 120;
    private static final int PUBLISH_CHECK_REFERENCE_LIMIT = 8;
    private static final Pattern MARKDOWN_IMAGE_LINK_PATTERN =
            Pattern.compile("!\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+[\"'][^\"']*[\"'])?\\)");
    private static final Pattern MARKDOWN_INLINE_LINK_PATTERN =
            Pattern.compile("!?\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+[\"'][^\"']*[\"'])?\\)");
    private static final Pattern BARE_URL_PATTERN = Pattern.compile("\\bhttps?://[^\\s<>\"')]+",
            Pattern.CASE_INSENSITIVE);

    public AIToolService() {
    }

    AIToolService(HttpClient client) {
        super(client);
    }

    public OptimizeWebsiteDescriptionResponse optimizeWebsiteDescription(OptimizeWebsiteDescriptionRequest optimizeRequest)
            throws IOException, InterruptedException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkAiConfig(info);
        List<AIResponseEntry.AIContentEntry> messages = List.of(
                new AIResponseEntry.AIContentEntry("system", buildWebsiteDescriptionSystemPrompt(info.getAi_prompt())),
                new AIResponseEntry.AIContentEntry("user", buildWebsiteDescriptionUserPrompt(optimizeRequest))
        );
        String content = requestCompletion(info, messages);
        OptimizeWebsiteDescriptionResponse response = new OptimizeWebsiteDescriptionResponse();
        response.setDescription(cleanDescription(content));
        return response;
    }

    public OptimizeAiPromptResponse optimizeAiPrompt(OptimizeAiPromptRequest optimizeRequest)
            throws IOException, InterruptedException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkAiConfig(info);
        List<AIResponseEntry.AIContentEntry> messages = List.of(
                new AIResponseEntry.AIContentEntry("system", loadPromptResource(AIPromptVO.getByToolKey("ai-prompt-optimize").getPromptPrefix(), AIPromptVO.getByToolKey("ai-prompt-optimize").getPromptFallback())),
                new AIResponseEntry.AIContentEntry("user", buildOptimizeAiPromptUserPrompt(optimizeRequest))
        );
        String content = requestCompletion(info, messages);
        OptimizeAiPromptResponse response = new OptimizeAiPromptResponse();
        response.setPrompt(cleanPrompt(content));
        return response;
    }

    public GenerateArticleTitleResponse generateArticleTitles(GenerateArticleTitleRequest generateRequest,
                                                              String conversationContext)
            throws IOException, InterruptedException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkAiConfig(info);
        List<AIResponseEntry.AIContentEntry> messages = List.of(
                new AIResponseEntry.AIContentEntry("system", loadPromptResource(AIPromptVO.getByToolKey("article-title-generate").getPromptPrefix(), AIPromptVO.getByToolKey("article-title-generate").getPromptFallback())),
                new AIResponseEntry.AIContentEntry("user",
                        buildGenerateArticleTitleUserPrompt(generateRequest, conversationContext))
        );
        String content = requestCompletion(info, messages);
        GenerateArticleTitleResponse response = new GenerateArticleTitleResponse();
        response.setTitles(parseTitles(content));
        return response;
    }

    public ScoreArticleResponse scoreArticle(ScoreArticleRequest scoreRequest, String conversationContext)
            throws IOException, InterruptedException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkAiConfig(info);
        List<AIResponseEntry.AIContentEntry> messages = List.of(
                new AIResponseEntry.AIContentEntry("system", loadPromptResource(AIPromptVO.getByToolKey("article-score").getPromptPrefix(), AIPromptVO.getByToolKey("article-score").getPromptFallback())),
                new AIResponseEntry.AIContentEntry("user", buildScoreArticleUserPrompt(scoreRequest, conversationContext))
        );
        String content = requestCompletion(info, messages);
        return parseArticleScore(content);
    }

    public GenerateArticleMarkdownResponse rewriteArticleMarkdown(GenerateArticleFieldRequest generateRequest,
                                                                  String conversationContext)
            throws IOException, InterruptedException {
        if (generateRequest.getMarkdown() == null
                || generateRequest.getMarkdown().trim().length() < REWRITE_MIN_MARKDOWN_LENGTH) {
            throw new ArgsException("markdown");
        }
        String content = requestArticleFieldCompletion(generateRequest,
                AIPromptVO.getByToolKey("article-markdown-rewrite"), conversationContext);
        return parseArticleMarkdown(content);
    }

    public ScoreArticleResponse publishCheckArticle(GenerateArticleFieldRequest generateRequest,
                                                    String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-publish-check"),
                conversationContext);
        return parseArticleScore(content);
    }

    public GenerateArticleAliasResponse generateArticleAlias(GenerateArticleFieldRequest generateRequest,
                                                             String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-alias-generate"), conversationContext);
        GenerateArticleAliasResponse response = new GenerateArticleAliasResponse();
        response.setAlias(cleanAlias(parseStringField(content, "alias", "AI alias response is invalid")));
        return response;
    }

    public GenerateArticleDigestResponse generateArticleDigest(GenerateArticleFieldRequest generateRequest,
                                                               String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-digest-generate"), conversationContext);
        GenerateArticleDigestResponse response = new GenerateArticleDigestResponse();
        response.setDigest(cleanPrompt(parseStringField(content, "digest", "AI digest response is invalid")));
        return response;
    }

    public GenerateArticleTagsResponse generateArticleTags(GenerateArticleFieldRequest generateRequest,
                                                           String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-tags-generate"), conversationContext);
        GenerateArticleTagsResponse response = new GenerateArticleTagsResponse();
        response.setTags(parseTags(content));
        return response;
    }

    public ArticleSeoCheckResponse checkArticleSeo(GenerateArticleFieldRequest generateRequest,
                                                   String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-seo-check"), conversationContext);
        return parseArticleSeo(content);
    }

    public ArticleProofreadResponse proofreadArticle(GenerateArticleFieldRequest generateRequest,
                                                     String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-proofread"), conversationContext);
        return parseArticleProofread(content);
    }

    public ArticleStructureAdviceResponse adviseArticleStructure(GenerateArticleFieldRequest generateRequest,
                                                                 String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-structure-advice"), conversationContext);
        return parseArticleStructure(content);
    }

    public ArticleReaderQuestionsResponse generateReaderQuestions(GenerateArticleFieldRequest generateRequest,
                                                                  String conversationContext)
            throws IOException, InterruptedException {
        String content = requestArticleFieldCompletion(generateRequest, AIPromptVO.getByToolKey("article-reader-questions"), conversationContext);
        return parseReaderQuestions(content);
    }

    private String buildWebsiteDescriptionSystemPrompt(String globalPrompt) {
        String basePrompt = StringUtils.isNotEmpty(globalPrompt) ? globalPrompt + "\n\n" : "";
        return basePrompt + loadPromptResource(AIPromptVO.getByToolKey("website-description").getPromptPrefix(), AIPromptVO.getByToolKey("website-description").getPromptFallback());
    }

    private String buildWebsiteDescriptionUserPrompt(OptimizeWebsiteDescriptionRequest request) {
        return loadPromptResource(AIPromptVO.getByToolKey("website-description").getInputPrefix(), AIPromptVO.getByToolKey("website-description").getInputFallback())
                .replace("{{title}}", emptyToBlank(request.getTitle()))
                .replace("{{second_title}}", emptyToBlank(request.getSecond_title()))
                .replace("{{keywords}}", emptyToBlank(request.getKeywords()))
                .replace("{{description}}", emptyToBlank(request.getDescription()))
                .replace("{{author}}", emptyToBlank(request.getAuthor()));
    }

    private String buildOptimizeAiPromptUserPrompt(OptimizeAiPromptRequest request) {
        return loadPromptResource(AIPromptVO.getByToolKey("ai-prompt-optimize").getInputPrefix(), AIPromptVO.getByToolKey("ai-prompt-optimize").getInputFallback())
                .replace("{{prompt}}", emptyToBlank(request.getPrompt()));
    }

    private String buildGenerateArticleTitleUserPrompt(GenerateArticleTitleRequest request, String conversationContext) {
        String prompt = loadPromptResource(AIPromptVO.getByToolKey("article-title-generate").getInputPrefix(), AIPromptVO.getByToolKey("article-title-generate").getInputFallback())
                .replace("{{title}}", emptyToBlank(request.getTitle()))
                .replace("{{digest}}", emptyToBlank(request.getDigest()))
                .replace("{{keywords}}", emptyToBlank(request.getKeywords()))
                .replace("{{markdown}}", emptyToBlank(request.getMarkdown()));
        return appendConversationContext(appendSelectedTextContext(prompt, request.getSelectedText()),
                conversationContext);
    }

    private String buildScoreArticleUserPrompt(ScoreArticleRequest request, String conversationContext) {
        String prompt = loadPromptResource(AIPromptVO.getByToolKey("article-score").getInputPrefix(), AIPromptVO.getByToolKey("article-score").getInputFallback())
                .replace("{{title}}", emptyToBlank(request.getTitle()))
                .replace("{{digest}}", emptyToBlank(request.getDigest()))
                .replace("{{keywords}}", emptyToBlank(request.getKeywords()))
                .replace("{{markdown}}", emptyToBlank(request.getMarkdown()));
        return appendConversationContext(appendSelectedTextContext(prompt, request.getSelectedText()),
                conversationContext);
    }

    private String requestArticleFieldCompletion(GenerateArticleFieldRequest request, AIPromptVO promptVO, String conversationContext)
            throws IOException, InterruptedException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkAiConfig(info);
        List<AIResponseEntry.AIContentEntry> messages = List.of(
                new AIResponseEntry.AIContentEntry("system", loadPromptResource(promptVO.getPromptPrefix(), promptVO.getPromptFallback())),
                new AIResponseEntry.AIContentEntry("user",
                        buildArticleFieldUserPrompt(request, promptVO, conversationContext))
        );
        return requestCompletion(info, messages);
    }

    private String buildArticleFieldUserPrompt(GenerateArticleFieldRequest request, AIPromptVO promptVO, String conversationContext) {
        String prompt = loadPromptResource(promptVO.getInputPrefix(), promptVO.getInputFallback())
                .replace("{{title}}", emptyToBlank(request.getTitle()))
                .replace("{{digest}}", emptyToBlank(request.getDigest()))
                .replace("{{keywords}}", emptyToBlank(request.getKeywords()))
                .replace("{{markdown}}", emptyToBlank(request.getMarkdown()))
                .replace("{{resourceSummary}}", buildMarkdownReferenceSummary(request.getMarkdown()))
                .replace("{{publishContext}}", buildPublishContextSummary(request));
        return appendConversationContext(appendSelectedTextContext(prompt, request.getSelectedText()),
                conversationContext);
    }

    String buildPublishContextSummary(GenerateArticleFieldRequest request) {
        boolean hasAlias = StringUtils.isNotEmpty(request.getAlias());
        boolean hasCover = StringUtils.isNotEmpty(request.getThumbnail());
        boolean staticSiteEnabled = Objects.equals(request.getStaticSiteEnabled(), true);
        boolean staticSitePluginEnabled = Objects.equals(request.getStaticSitePluginEnabled(), true);
        boolean transparentPublish = Objects.equals(request.getTransparentPublish(), true);
        return "aliasStatus: " + (hasAlias ? "present" : "empty")
                + "\ncoverStatus: " + (hasCover ? "present" : "empty")
                + "\ntransparentPublish: " + transparentPublish
                + "\nstaticSiteEnabled: " + staticSiteEnabled
                + "\nstaticSitePluginEnabled: " + staticSitePluginEnabled
                + "\nstaticSyncExpected: " + (transparentPublish && staticSiteEnabled && staticSitePluginEnabled)
                + "\nstructuredDataBoundary: theme-owned-public-output"
                + "\nstructuredDataInputs: title,digest,keywords,cover,articleBody"
                + "\naiPublishCheckUsesConfiguredProvider: true"
                + "\naiPublishCheckBlocksPublishing: false";
    }

    String buildMarkdownReferenceSummary(String markdown) {
        String content = emptyToBlank(markdown);
        Set<String> imageReferences = collectReferences(content, MARKDOWN_IMAGE_LINK_PATTERN);
        Set<String> linkReferences = collectReferences(content, MARKDOWN_INLINE_LINK_PATTERN);
        Set<String> externalLinks = new LinkedHashSet<>();
        linkReferences.stream()
                .filter(this::isExternalLink)
                .forEach(externalLinks::add);
        Matcher bareUrlMatcher = BARE_URL_PATTERN.matcher(content);
        while (bareUrlMatcher.find()) {
            externalLinks.add(bareUrlMatcher.group());
        }
        return "imageReferenceCount: " + imageReferences.size()
                + "\nimageReferences:\n" + formatReferenceList(imageReferences)
                + "\nlinkReferenceCount: " + linkReferences.size()
                + "\nlinkReferences:\n" + formatReferenceList(linkReferences)
                + "\nexternalLinkCount: " + externalLinks.size()
                + "\nexternalLinks:\n" + formatReferenceList(externalLinks);
    }

    private Set<String> collectReferences(String markdown, Pattern pattern) {
        Set<String> references = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(markdown);
        while (matcher.find()) {
            references.add(matcher.group(1));
        }
        return references;
    }

    private boolean isExternalLink(String link) {
        return link != null && link.toLowerCase().matches("^https?://.*");
    }

    private String formatReferenceList(Set<String> references) {
        if (references.isEmpty()) {
            return "- none";
        }
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (String reference : references) {
            if (index >= PUBLISH_CHECK_REFERENCE_LIMIT) {
                sb.append("- ... ").append(references.size() - PUBLISH_CHECK_REFERENCE_LIMIT).append(" more");
                break;
            }
            sb.append("- ").append(reference).append("\n");
            index++;
        }
        return sb.toString().trim();
    }

    private String appendConversationContext(String prompt, String conversationContext) {
        if (StringUtils.isEmpty(conversationContext)) {
            return prompt;
        }
        return prompt + "\n\n" + conversationContext;
    }

    private String cleanDescription(String content) {
        String cleaned = cleanPrompt(content);
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    private String cleanPrompt(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = content.trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private List<String> parseTitles(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        Object titlesObj = responseMap.get("titles");
        if (!(titlesObj instanceof List)) {
            throw new AIResponseException("title response is invalid");
        }
        List<String> titles = new ArrayList<>();
        for (Object titleObj : (List<?>) titlesObj) {
            if (titleObj != null && StringUtils.isNotEmpty(titleObj.toString())) {
                titles.add(cleanPrompt(titleObj.toString()));
            }
            if (titles.size() >= 3) {
                break;
            }
        }
        if (titles.isEmpty()) {
            throw new AIResponseException("title response is empty");
        }
        return titles;
    }

    private String parseStringField(String content, String fieldName, String errorMessage) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        Object value = responseMap.get(fieldName);
        if (value == null || StringUtils.isEmpty(value.toString())) {
            throw new AIResponseException(errorMessage);
        }
        return value.toString();
    }

    private GenerateArticleMarkdownResponse parseArticleMarkdown(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        GenerateArticleMarkdownResponse response = new GenerateArticleMarkdownResponse();
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        response.setMarkdown(cleanPrompt(valueToString(responseMap.get("markdown"))));
        if (StringUtils.isEmpty(response.getMarkdown())) {
            throw new AIResponseException("markdown response is invalid");
        }
        return response;
    }

    private List<String> parseTags(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        Object tagsObj = responseMap.get("tags");
        if (!(tagsObj instanceof List)) {
            throw new AIResponseException("tags response is invalid");
        }
        List<String> tags = new ArrayList<>();
        for (Object tagObj : (List<?>) tagsObj) {
            if (tagObj != null && StringUtils.isNotEmpty(tagObj.toString())) {
                String tag = cleanPrompt(tagObj.toString());
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
            }
            if (tags.size() >= 6) {
                break;
            }
        }
        if (tags.isEmpty()) {
            throw new AIResponseException("tags response is empty");
        }
        return tags;
    }

    private String cleanAlias(String alias) {
        String cleaned = cleanPrompt(alias).toLowerCase()
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (StringUtils.isEmpty(cleaned)) {
            throw new AIResponseException("alias response is empty");
        }
        return cleaned;
    }

    private ScoreArticleResponse parseArticleScore(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        ScoreArticleResponse response = new ScoreArticleResponse();
        response.setScore(normalizeScore(responseMap.get("score")));
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        Object itemsObj = responseMap.get("items");
        List<ScoreArticleResponse.ScoreItem> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object itemObj : (List<?>) itemsObj) {
                if (!(itemObj instanceof Map)) {
                    continue;
                }
                Map itemMap = (Map) itemObj;
                ScoreArticleResponse.ScoreItem item = new ScoreArticleResponse.ScoreItem();
                item.setName(cleanPrompt(valueToString(itemMap.get("name"))));
                item.setScore(normalizeScore(itemMap.get("score")));
                item.setSuggestion(cleanPrompt(valueToString(itemMap.get("suggestion"))));
                if (StringUtils.isNotEmpty(item.getName()) || StringUtils.isNotEmpty(item.getSuggestion())) {
                    items.add(item);
                }
            }
        }
        if (StringUtils.isEmpty(response.getSummary()) || items.isEmpty()) {
            throw new AIResponseException("score response is invalid");
        }
        response.setItems(items);
        return response;
    }

    private ArticleSeoCheckResponse parseArticleSeo(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        ArticleSeoCheckResponse response = new ArticleSeoCheckResponse();
        response.setScore(normalizeScore(responseMap.get("score")));
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        Object itemsObj = responseMap.get("items");
        List<ArticleSeoCheckResponse.SeoItem> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object itemObj : (List<?>) itemsObj) {
                if (!(itemObj instanceof Map)) {
                    continue;
                }
                Map itemMap = (Map) itemObj;
                ArticleSeoCheckResponse.SeoItem item = new ArticleSeoCheckResponse.SeoItem();
                item.setName(cleanPrompt(valueToString(itemMap.get("name"))));
                item.setStatus(cleanPrompt(valueToString(itemMap.get("status"))));
                item.setSuggestion(cleanPrompt(valueToString(itemMap.get("suggestion"))));
                if (StringUtils.isNotEmpty(item.getName()) || StringUtils.isNotEmpty(item.getSuggestion())) {
                    items.add(item);
                }
            }
        }
        if (StringUtils.isEmpty(response.getSummary()) || items.isEmpty()) {
            throw new AIResponseException("SEO response is invalid");
        }
        response.setItems(items);
        return response;
    }

    private ArticleProofreadResponse parseArticleProofread(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        ArticleProofreadResponse response = new ArticleProofreadResponse();
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        Object itemsObj = responseMap.get("items");
        List<ArticleProofreadResponse.ProofreadItem> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object itemObj : (List<?>) itemsObj) {
                if (!(itemObj instanceof Map)) {
                    continue;
                }
                Map itemMap = (Map) itemObj;
                ArticleProofreadResponse.ProofreadItem item = new ArticleProofreadResponse.ProofreadItem();
                item.setOriginal(cleanPrompt(valueToString(itemMap.get("original"))));
                item.setIssue(cleanPrompt(valueToString(itemMap.get("issue"))));
                item.setSuggestion(cleanPrompt(valueToString(itemMap.get("suggestion"))));
                if (StringUtils.isNotEmpty(item.getOriginal()) || StringUtils.isNotEmpty(item.getSuggestion())) {
                    items.add(item);
                }
            }
        }
        if (StringUtils.isEmpty(response.getSummary())) {
            throw new AIResponseException("proofread response is invalid");
        }
        response.setItems(items);
        return response;
    }

    private ArticleStructureAdviceResponse parseArticleStructure(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        ArticleStructureAdviceResponse response = new ArticleStructureAdviceResponse();
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        Object itemsObj = responseMap.get("items");
        List<ArticleStructureAdviceResponse.StructureItem> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object itemObj : (List<?>) itemsObj) {
                if (!(itemObj instanceof Map)) {
                    continue;
                }
                Map itemMap = (Map) itemObj;
                ArticleStructureAdviceResponse.StructureItem item =
                        new ArticleStructureAdviceResponse.StructureItem();
                item.setName(cleanPrompt(valueToString(itemMap.get("name"))));
                item.setStatus(cleanPrompt(valueToString(itemMap.get("status"))));
                item.setSuggestion(cleanPrompt(valueToString(itemMap.get("suggestion"))));
                if (StringUtils.isNotEmpty(item.getName()) || StringUtils.isNotEmpty(item.getSuggestion())) {
                    items.add(item);
                }
            }
        }
        if (StringUtils.isEmpty(response.getSummary()) || items.isEmpty()) {
            throw new AIResponseException("structure response is invalid");
        }
        response.setItems(items);
        return response;
    }

    private ArticleReaderQuestionsResponse parseReaderQuestions(String content) {
        String jsonContent = cleanJsonContent(content);
        Map responseMap = gson.fromJson(jsonContent, Map.class);
        ArticleReaderQuestionsResponse response = new ArticleReaderQuestionsResponse();
        response.setSummary(cleanPrompt(valueToString(responseMap.get("summary"))));
        Object itemsObj = responseMap.get("items");
        List<ArticleReaderQuestionsResponse.ReaderQuestionItem> items = new ArrayList<>();
        if (itemsObj instanceof List) {
            for (Object itemObj : (List<?>) itemsObj) {
                if (!(itemObj instanceof Map)) {
                    continue;
                }
                Map itemMap = (Map) itemObj;
                ArticleReaderQuestionsResponse.ReaderQuestionItem item =
                        new ArticleReaderQuestionsResponse.ReaderQuestionItem();
                item.setQuestion(cleanPrompt(valueToString(itemMap.get("question"))));
                item.setReason(cleanPrompt(valueToString(itemMap.get("reason"))));
                item.setSuggestion(cleanPrompt(valueToString(itemMap.get("suggestion"))));
                if (StringUtils.isNotEmpty(item.getQuestion())) {
                    items.add(item);
                }
            }
        }
        if (StringUtils.isEmpty(response.getSummary()) || items.isEmpty()) {
            throw new AIResponseException("reader questions response is invalid");
        }
        response.setItems(items);
        return response;
    }

    private int normalizeScore(Object value) {
        int score = 0;
        if (value instanceof Number) {
            score = (int) Math.round(((Number) value).doubleValue());
        } else if (value != null && StringUtils.isNotEmpty(value.toString())) {
            try {
                score = (int) Math.round(Double.parseDouble(value.toString()));
            } catch (NumberFormatException ignored) {
                score = 0;
            }
        }
        return Math.min(100, Math.max(0, score));
    }

    private String valueToString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String cleanJsonContent(String content) {
        String cleaned = content == null ? "" : content.trim();
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf('\n');
            int lastFenceStart = cleaned.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFenceStart > firstLineEnd) {
                cleaned = cleaned.substring(firstLineEnd + 1, lastFenceStart).trim();
            }
        }
        return cleaned;
    }
}
