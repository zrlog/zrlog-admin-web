package com.zrlog.admin.business.rest.response;

import java.util.List;

public class ArticleVersionCompareResponse {

    private Integer fromVersion;
    private Integer toVersion;
    private LoadEditArticleResponse fromArticle;
    private LoadEditArticleResponse toArticle;
    private List<String> changedFields;

    public Integer getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(Integer fromVersion) {
        this.fromVersion = fromVersion;
    }

    public Integer getToVersion() {
        return toVersion;
    }

    public void setToVersion(Integer toVersion) {
        this.toVersion = toVersion;
    }

    public LoadEditArticleResponse getFromArticle() {
        return fromArticle;
    }

    public void setFromArticle(LoadEditArticleResponse fromArticle) {
        this.fromArticle = fromArticle;
    }

    public LoadEditArticleResponse getToArticle() {
        return toArticle;
    }

    public void setToArticle(LoadEditArticleResponse toArticle) {
        this.toArticle = toArticle;
    }

    public List<String> getChangedFields() {
        return changedFields;
    }

    public void setChangedFields(List<String> changedFields) {
        this.changedFields = changedFields;
    }
}
