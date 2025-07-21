package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.type.AIProviderType;

import java.util.List;

public class AIWebSiteInfoResponse extends AIWebSiteInfo {

    private List<AIProvider> allProviders;

    public List<AIProvider> getAllProviders() {
        return allProviders;
    }

    public void setAllProviders(List<AIProvider> allProviders) {
        this.allProviders = allProviders;
    }

    public static class AIProvider {
        private AIProviderType name;
        private List<String> models;

        public AIProviderType getName() {
            return name;
        }

        public void setName(AIProviderType name) {
            this.name = name;
        }

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = models;
        }
    }
}
