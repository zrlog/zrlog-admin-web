package com.zrlog.admin.business.rest.response;

import java.util.List;

public class MessageCenterOperationNoticeEntry {

    private String taskKey;
    private String title;
    private String description;
    private String actionLabel;
    private String actionPath;
    private String source;
    private String status;
    private Boolean closable;
    private Long createdAt;
    private Long updatedAt;
    private Long readAt;
    private Object payload;

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionPath() {
        return actionPath;
    }

    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getClosable() {
        return closable;
    }

    public void setClosable(Boolean closable) {
        this.closable = closable;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getReadAt() {
        return readAt;
    }

    public void setReadAt(Long readAt) {
        this.readAt = readAt;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public static class ReplaceArticleResourceUrlPayload {

        private Integer scannedArticles;
        private Integer updatedArticles;
        private Integer updatedFields;

        public ReplaceArticleResourceUrlPayload() {
        }

        public ReplaceArticleResourceUrlPayload(Integer scannedArticles, Integer updatedArticles,
                                                Integer updatedFields) {
            this.scannedArticles = scannedArticles;
            this.updatedArticles = updatedArticles;
            this.updatedFields = updatedFields;
        }

        public Integer getScannedArticles() {
            return scannedArticles;
        }

        public void setScannedArticles(Integer scannedArticles) {
            this.scannedArticles = scannedArticles;
        }

        public Integer getUpdatedArticles() {
            return updatedArticles;
        }

        public void setUpdatedArticles(Integer updatedArticles) {
            this.updatedArticles = updatedArticles;
        }

        public Integer getUpdatedFields() {
            return updatedFields;
        }

        public void setUpdatedFields(Integer updatedFields) {
            this.updatedFields = updatedFields;
        }
    }

    public static class StaticSiteSyncPayload {

        private List<String> siteTypes;
        private Boolean synced;

        public StaticSiteSyncPayload() {
        }

        public StaticSiteSyncPayload(List<String> siteTypes, Boolean synced) {
            this.siteTypes = siteTypes;
            this.synced = synced;
        }

        public List<String> getSiteTypes() {
            return siteTypes;
        }

        public void setSiteTypes(List<String> siteTypes) {
            this.siteTypes = siteTypes;
        }

        public Boolean getSynced() {
            return synced;
        }

        public void setSynced(Boolean synced) {
            this.synced = synced;
        }
    }

    public static class UpgradePayload {

        private Boolean finish;
        private String message;

        public UpgradePayload() {
        }

        public UpgradePayload(Boolean finish, String message) {
            this.finish = finish;
            this.message = message;
        }

        public Boolean getFinish() {
            return finish;
        }

        public void setFinish(Boolean finish) {
            this.finish = finish;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class UpgradeRestartPayload {

        private String buildId;

        public UpgradeRestartPayload() {
        }

        public UpgradeRestartPayload(String buildId) {
            this.buildId = buildId;
        }

        public String getBuildId() {
            return buildId;
        }

        public void setBuildId(String buildId) {
            this.buildId = buildId;
        }
    }

    public static class PublishCheckPayload {

        private Long articleId;
        private String articleTitle;
        private Integer score;
        private Integer itemCount;

        public PublishCheckPayload() {
        }

        public PublishCheckPayload(Long articleId, String articleTitle, Integer score, Integer itemCount) {
            this.articleId = articleId;
            this.articleTitle = articleTitle;
            this.score = score;
            this.itemCount = itemCount;
        }

        public Long getArticleId() {
            return articleId;
        }

        public void setArticleId(Long articleId) {
            this.articleId = articleId;
        }

        public String getArticleTitle() {
            return articleTitle;
        }

        public void setArticleTitle(String articleTitle) {
            this.articleTitle = articleTitle;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public Integer getItemCount() {
            return itemCount;
        }

        public void setItemCount(Integer itemCount) {
            this.itemCount = itemCount;
        }
    }
}
