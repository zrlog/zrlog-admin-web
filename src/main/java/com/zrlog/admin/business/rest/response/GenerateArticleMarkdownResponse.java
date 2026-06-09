package com.zrlog.admin.business.rest.response;

public class GenerateArticleMarkdownResponse {

    private String summary;
    private String markdown;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
