package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class TagManagementPreviewResponse {

    private String sourceTag;
    private String targetTag;
    private String operation;
    private long affectedArticleCount;
    private long updatedArticleCount;
    private boolean hasMore;
    private boolean executed;
    private List<TagManagementArticleImpactResponse> articles = new ArrayList<>();

    public String getSourceTag() {
        return sourceTag;
    }

    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }

    public String getTargetTag() {
        return targetTag;
    }

    public void setTargetTag(String targetTag) {
        this.targetTag = targetTag;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getAffectedArticleCount() {
        return affectedArticleCount;
    }

    public void setAffectedArticleCount(long affectedArticleCount) {
        this.affectedArticleCount = affectedArticleCount;
    }

    public long getUpdatedArticleCount() {
        return updatedArticleCount;
    }

    public void setUpdatedArticleCount(long updatedArticleCount) {
        this.updatedArticleCount = updatedArticleCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public List<TagManagementArticleImpactResponse> getArticles() {
        return articles;
    }

    public void setArticles(List<TagManagementArticleImpactResponse> articles) {
        this.articles = articles;
    }
}
