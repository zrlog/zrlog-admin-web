package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.ai.model.AIProviderType;
import java.util.List;

public class AIArticleGlobalResponse extends ArticleGlobalResponse {
    private AIProviderType aiProvider;
    private String aiModel;
    private Boolean aiConfigured;
    private List<AIResponseEntry.AIContentEntry> aiMessages;

    public AIArticleGlobalResponse() { }

    public AIArticleGlobalResponse(ArticleGlobalResponse article) {
        setArticle(article.getArticle());
        setTags(article.getTags());
        setTypes(article.getTypes());
        setLinkPreviewEnabled(article.getLinkPreviewEnabled());
        setPublishCheckEnabled(article.getPublishCheckEnabled());
        setArticleCoverAspectRatio(article.getArticleCoverAspectRatio());
        setArticleEditAutoSaveInterval(article.getArticleEditAutoSaveInterval());
    }

    public AIProviderType getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(AIProviderType aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Boolean getAiConfigured() {
        return aiConfigured;
    }

    public void setAiConfigured(Boolean aiConfigured) {
        this.aiConfigured = aiConfigured;
    }

    public List<AIResponseEntry.AIContentEntry> getAiMessages() {
        return aiMessages;
    }

    public void setAiMessages(List<AIResponseEntry.AIContentEntry> aiMessages) {
        this.aiMessages = aiMessages;
    }

}
