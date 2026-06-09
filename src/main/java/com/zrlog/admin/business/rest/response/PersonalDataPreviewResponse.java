package com.zrlog.admin.business.rest.response;

public class PersonalDataPreviewResponse {

    private String query;
    private long commentCount;
    private long commentArticleCount;
    private String latestCommentTime;
    private boolean adminUserMatched;
    private boolean adminEmailMatched;
    private boolean pluginDataRequiresPlugin = true;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getCommentArticleCount() {
        return commentArticleCount;
    }

    public void setCommentArticleCount(long commentArticleCount) {
        this.commentArticleCount = commentArticleCount;
    }

    public String getLatestCommentTime() {
        return latestCommentTime;
    }

    public void setLatestCommentTime(String latestCommentTime) {
        this.latestCommentTime = latestCommentTime;
    }

    public boolean isAdminUserMatched() {
        return adminUserMatched;
    }

    public void setAdminUserMatched(boolean adminUserMatched) {
        this.adminUserMatched = adminUserMatched;
    }

    public boolean isAdminEmailMatched() {
        return adminEmailMatched;
    }

    public void setAdminEmailMatched(boolean adminEmailMatched) {
        this.adminEmailMatched = adminEmailMatched;
    }

    public boolean isPluginDataRequiresPlugin() {
        return pluginDataRequiresPlugin;
    }

    public void setPluginDataRequiresPlugin(boolean pluginDataRequiresPlugin) {
        this.pluginDataRequiresPlugin = pluginDataRequiresPlugin;
    }
}
