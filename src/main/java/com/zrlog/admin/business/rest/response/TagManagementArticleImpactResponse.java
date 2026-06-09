package com.zrlog.admin.business.rest.response;

public class TagManagementArticleImpactResponse {

    private Long id;
    private String title;
    private String beforeKeywords;
    private String afterKeywords;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBeforeKeywords() {
        return beforeKeywords;
    }

    public void setBeforeKeywords(String beforeKeywords) {
        this.beforeKeywords = beforeKeywords;
    }

    public String getAfterKeywords() {
        return afterKeywords;
    }

    public void setAfterKeywords(String afterKeywords) {
        this.afterKeywords = afterKeywords;
    }
}
