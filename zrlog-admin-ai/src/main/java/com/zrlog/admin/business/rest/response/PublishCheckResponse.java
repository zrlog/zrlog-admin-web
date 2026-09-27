package com.zrlog.admin.business.rest.response;

import java.util.List;

public class PublishCheckResponse {

    private PublishCheckToolPayload toolPayload;
    private String content;
    private String messageId;
    private List<AIResponseEntry.AIContentEntry> aiMessages;

    public PublishCheckResponse() {
    }

    public PublishCheckResponse(PublishCheckToolPayload toolPayload, String content, String messageId,
                                List<AIResponseEntry.AIContentEntry> aiMessages) {
        this.toolPayload = toolPayload;
        this.content = content;
        this.messageId = messageId;
        this.aiMessages = aiMessages;
    }

    public PublishCheckToolPayload getToolPayload() {
        return toolPayload;
    }

    public void setToolPayload(PublishCheckToolPayload toolPayload) {
        this.toolPayload = toolPayload;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public List<AIResponseEntry.AIContentEntry> getAiMessages() {
        return aiMessages;
    }

    public void setAiMessages(List<AIResponseEntry.AIContentEntry> aiMessages) {
        this.aiMessages = aiMessages;
    }
}
