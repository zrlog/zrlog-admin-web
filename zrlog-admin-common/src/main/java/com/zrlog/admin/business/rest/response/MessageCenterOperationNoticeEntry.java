package com.zrlog.admin.business.rest.response;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class MessageCenterOperationNoticeEntry {

    private String taskKey;
    private String titleKey;
    private String descriptionKey;
    private String actionLabelKey;
    private String actionPath;
    private String sourceKey;
    private String status;
    private Boolean closable;
    private Long createdAt;
    private Long updatedAt;
    private Long readAt;
    private Object payload;
    private Map<String, String> descriptionArguments = new LinkedHashMap<>();

    public Map<String, String> getDescriptionArguments() {
        return descriptionArguments;
    }

    public void setDescriptionArguments(Map<String, String> arguments) {
        descriptionArguments = arguments;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public void setTitleKey(String titleKey) {
        this.titleKey = titleKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    public String getActionLabelKey() {
        return actionLabelKey;
    }

    public void setActionLabelKey(String actionLabelKey) {
        this.actionLabelKey = actionLabelKey;
    }

    public String getActionPath() {
        return actionPath;
    }

    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
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

        public UpgradePayload() {
        }

        public UpgradePayload(Boolean finish) {
            this.finish = finish;
        }

        public Boolean getFinish() {
            return finish;
        }

        public void setFinish(Boolean finish) {
            this.finish = finish;
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
