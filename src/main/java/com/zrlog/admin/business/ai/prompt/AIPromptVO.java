package com.zrlog.admin.business.ai.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIPromptVO {

    private String toolKey;
    private String promptPrefix;
    private String inputPrefix;

    public AIPromptVO(String toolKey, String promptPrefix, String inputPrefix) {
        this.toolKey = toolKey;
        this.promptPrefix = promptPrefix;
        this.inputPrefix = inputPrefix;
    }

    public String getToolKey() {
        return toolKey;
    }

    public String getPromptPrefix() {
        return promptPrefix;
    }

    public String getPromptFallback() {
        return promptPrefix != null ? promptPrefix + "zh_CN.md" : null;
    }

    public String getInputPrefix() {
        return inputPrefix;
    }

    public String getInputFallback() {
        return inputPrefix != null ? inputPrefix + "zh_CN.md" : null;
    }

    private static final Map<String, AIPromptVO> PROMPTS = new HashMap<>();

    static {
        register("website-description", "/ai-prompts/website-description_", "/ai-prompts/website-description-input_");
        register("ai-prompt-optimize", "/ai-prompts/ai-prompt-optimize_", "/ai-prompts/ai-prompt-optimize-input_");
        register("article-title-generate", "/ai-prompts/article-title-generate_", "/ai-prompts/article-title-generate-input_");
        register("article-markdown-rewrite", "/ai-prompts/article-markdown-rewrite_", "/ai-prompts/article-markdown-rewrite-input_");
        register("article-score", "/ai-prompts/article-score_", "/ai-prompts/article-score-input_");
        register("article-alias-generate", "/ai-prompts/article-alias-generate_", "/ai-prompts/article-alias-generate-input_");
        register("article-digest-generate", "/ai-prompts/article-digest-generate_", "/ai-prompts/article-digest-generate-input_");
        register("article-tags-generate", "/ai-prompts/article-tags-generate_", "/ai-prompts/article-tags-generate-input_");
        register("article-publish-check", "/ai-prompts/article-publish-check_", "/ai-prompts/article-publish-check-input_");
        register("article-seo-check", "/ai-prompts/article-seo-check_", "/ai-prompts/article-seo-check-input_");
        register("article-proofread", "/ai-prompts/article-proofread_", "/ai-prompts/article-proofread-input_");
        register("article-structure-advice", "/ai-prompts/article-structure-advice_", "/ai-prompts/article-structure-advice-input_");
        register("article-reader-questions", "/ai-prompts/article-reader-questions_", "/ai-prompts/article-reader-questions-input_");
        register("article-cover-generate", null, "/ai-prompts/article-cover-generate-input_");
    }

    private static void register(String toolKey, String promptPrefix, String inputPrefix) {
        PROMPTS.put(toolKey, new AIPromptVO(toolKey, promptPrefix, inputPrefix));
    }

    public static AIPromptVO getByToolKey(String toolKey) {
        return PROMPTS.get(toolKey);
    }

    public static List<AIPromptVO> getAll() {
        return new ArrayList<>(PROMPTS.values());
    }
}
