package com.zrlog.admin.business.ai.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AIProviderType {

    DEEP_SEEK("https://api.deepseek.com/chat/completions", null, Arrays.asList(
            new AIModelEntry("deepseek-chat", AIModelCapability.TEXT),
            new AIModelEntry("deepseek-reasoner", AIModelCapability.TEXT)
    )),
    OPEN_AI("https://api.openai.com/v1/completions", "https://api.openai.com/v1/images/generations",
            Arrays.asList(
                    new AIModelEntry("gpt-5.1", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-5-mini", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-4.1", AIModelCapability.TEXT),
                    new AIModelEntry("gpt-image-2", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gpt-image-1.5", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gpt-image-1-mini", AIModelCapability.IMAGE_GENERATION)
            )),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1", null, Arrays.asList(
            new AIModelEntry("qwen3-max", AIModelCapability.TEXT),
            new AIModelEntry("qwen3-max-2025-09-23", AIModelCapability.TEXT),
            new AIModelEntry("qwen3-max-preview", AIModelCapability.TEXT)
    )),
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "https://generativelanguage.googleapis.com/v1beta/openai/images/generations",
            Arrays.asList(
                    new AIModelEntry("gemini-3.1-flash-lite-preview", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-flash-latest", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3.1-pro-preview", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-pro-latest", AIModelCapability.TEXT),
                    new AIModelEntry("gemini-3.1-flash-image-preview", AIModelCapability.IMAGE_GENERATION),
                    new AIModelEntry("gemini-2.5-flash-image", AIModelCapability.IMAGE_GENERATION)
            ));

    private final String baseUrl;
    private final String imageGenerationBaseUrl;
    private final List<AIModelEntry> modelEntries;

    AIProviderType(String baseUrl, String imageGenerationBaseUrl, List<AIModelEntry> modelEntries) {
        this.baseUrl = baseUrl;
        this.imageGenerationBaseUrl = imageGenerationBaseUrl;
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

    public String getImageGenerationBaseUrl() {
        return imageGenerationBaseUrl;
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
