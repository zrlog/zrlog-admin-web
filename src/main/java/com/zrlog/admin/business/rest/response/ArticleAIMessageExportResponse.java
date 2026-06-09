package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticleAIMessageExportResponse {

    private Long articleId;
    private boolean draft;
    private long exportedAt;
    private int messageCount;
    private List<AIResponseEntry.AIContentEntry> messages = new ArrayList<>();

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public long getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(long exportedAt) {
        this.exportedAt = exportedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public List<AIResponseEntry.AIContentEntry> getMessages() {
        return messages;
    }

    public void setMessages(List<AIResponseEntry.AIContentEntry> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }
}
