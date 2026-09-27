package com.zrlog.admin.business.rest.base;

import com.zrlog.admin.business.rest.response.AIResponseEntry;

import java.util.List;

public class AIWebSiteInfoWithAIMessages extends AIWebSiteInfo {

    public List<AIResponseEntry.AIContentEntry> aiMessages;

    public List<AIResponseEntry.AIContentEntry> getAiMessages() {
        return aiMessages;
    }

    public void setAiMessages(List<AIResponseEntry.AIContentEntry> aiMessages) {
        this.aiMessages = aiMessages;
    }
}
