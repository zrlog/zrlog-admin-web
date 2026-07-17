package com.zrlog.admin.business.ai.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AIProviderType {

    DEEP_SEEK("https://api.deepseek.com", Arrays.asList(
            new AIModelEntry("deepseek-v4-pro", AIModelCapability.TEXT),
            new AIModelEntry("deepseek-v4-flash", AIModelCapability.TEXT),
            new AIModelEntry("deepseek-chat", AIModelCapability.TEXT),
            new AIModelEntry("deepseek-reasoner", AIModelCapability.TEXT)
    )),
    OPEN_AI("https://api.openai.com/v1",
            Arrays.asList(
                    new AIModelEntry("gpt-5.6", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5.6-sol", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5.6-terra", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5.6-luna", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5.1", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5-mini", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-4.1", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-image-2", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gpt-image-1.5", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gpt-image-1-mini", AIModelCapability.IMAGE_GENERATION)
            )),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1", Arrays.asList(
            new AIModelEntry("qwen3.7-max", AIModelCapability.TEXT),
            new AIModelEntry("qwen3.7-plus", AIModelCapability.TEXT),
            new AIModelEntry("qwen3.6-flash", AIModelCapability.TEXT),
            new AIModelEntry("qwen3-max", AIModelCapability.TEXT),
            new AIModelEntry("qwen3-max-2025-09-23", AIModelCapability.TEXT),
            new AIModelEntry("qwen3-max-preview", AIModelCapability.TEXT)
    )),
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta/openai",
            Arrays.asList(
                    new AIModelEntry("gemini-3.5-flash", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3.1-flash-lite", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3.1-pro-preview", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3-flash-preview", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-flash-latest", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-pro-latest", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3.1-flash-image", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gemini-3.1-flash-lite-image", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gemini-3-pro-image", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gemini-2.5-flash-image", AIModelCapability.IMAGE_GENERATION)
            ));

    private final String baseUrl;
    private final List<AIModelEntry> modelEntries;

    AIProviderType(String baseUrl, List<AIModelEntry> modelEntries) {
        this.baseUrl = baseUrl;
        this.modelEntries = modelEntries;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<String> getModels() {
        return getModelsByCapability(AIModelCapability.TEXT);
    }

    public List<String> getImageModels() {
        return getModelsByCapability(AIModelCapability.IMAGE_GENERATION);
    }

    public List<AIModelEntry> getModelEntries() {
        return modelEntries;
    }

    private List<String> getModelsByCapability(AIModelCapability capability) {
        return modelEntries.stream()
                .filter(model -> model.supports(capability))
                .map(AIModelEntry::getName)
                .collect(Collectors.toList());
    }
}
