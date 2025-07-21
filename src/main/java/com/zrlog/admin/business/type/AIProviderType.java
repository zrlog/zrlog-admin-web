package com.zrlog.admin.business.type;

import java.util.Arrays;
import java.util.List;

public enum AIProviderType {

    DEEP_SEEK("https://api.deepseek.com/chat/completions", Arrays.asList("deepseek-chat", "deepseek-reasoner")),
    OPEN_AI("https://api.openai.com/v1/completions", Arrays.asList("gpt-5.1", "gpt-5", "gpt-5-mini", "gpt-4.1")),
    QWEN("https://dashscope.aliyuncs.com/compatible-mode/v1", Arrays.asList("qwen3-max", "qwen3-max-2025-09-23", "qwen3-max-preview")),
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", Arrays.asList("gemini-3.1-flash-lite-preview", "gemini-flash-latest", "gemini-3.1-pro-preview", "gemini-pro-latest"));

    private final String baseUrl;
    private final List<String> models;

    AIProviderType(String baseUrl, List<String> models) {
        this.baseUrl = baseUrl;
        this.models = models;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<String> getModels() {
        return models;
    }
}
