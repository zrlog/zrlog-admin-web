package com.zrlog.admin.business.ai.model;

import java.util.List;
import java.util.stream.Collectors;

public enum AIProviderType {

    DEEP_SEEK("https://api.deepseek.com"),
    OPEN_AI("https://api.openai.com/v1"),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1"),
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta/openai");

    private final String baseUrl;

    AIProviderType(String baseUrl) {
        this.baseUrl = baseUrl;
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
        return AIModelCatalog.getModels(this);
    }

    private List<String> getModelsByCapability(AIModelCapability capability) {
        return getModelEntries().stream()
                .filter(model -> model.supports(capability))
                .map(AIModelEntry::getName)
                .collect(Collectors.toList());
    }
}
