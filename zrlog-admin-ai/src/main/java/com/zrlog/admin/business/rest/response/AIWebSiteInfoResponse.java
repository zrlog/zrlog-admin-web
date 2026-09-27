package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.ai.model.AIModelEntry;
import com.zrlog.admin.business.ai.model.AIProviderType;

import java.util.List;

public class AIWebSiteInfoResponse extends AIWebSiteInfo {

    private List<AIProvider> allProviders;
    private List<AIProvider> allImageProviders;
    private boolean hasAiApiKey;
    private boolean hasAiImageApiKey;

    public List<AIProvider> getAllProviders() {
        return allProviders;
    }

    public void setAllProviders(List<AIProvider> allProviders) {
        this.allProviders = allProviders;
    }

    public List<AIProvider> getAllImageProviders() {
        return allImageProviders;
    }

    public void setAllImageProviders(List<AIProvider> allImageProviders) {
        this.allImageProviders = allImageProviders;
    }

    public boolean isHasAiApiKey() {
        return hasAiApiKey;
    }

    public void setHasAiApiKey(boolean hasAiApiKey) {
        this.hasAiApiKey = hasAiApiKey;
    }

    public boolean isHasAiImageApiKey() {
        return hasAiImageApiKey;
    }

    public void setHasAiImageApiKey(boolean hasAiImageApiKey) {
        this.hasAiImageApiKey = hasAiImageApiKey;
    }

    public static class AIProvider {
        private AIProviderType name;
        private String baseUrl;
        private List<String> models;
        private List<AIModelEntry> modelEntries;

        public AIProviderType getName() {
            return name;
        }

        public void setName(AIProviderType name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = models;
        }

        public List<AIModelEntry> getModelEntries() {
            return modelEntries;
        }

        public void setModelEntries(List<AIModelEntry> modelEntries) {
            this.modelEntries = modelEntries;
        }
    }
}
