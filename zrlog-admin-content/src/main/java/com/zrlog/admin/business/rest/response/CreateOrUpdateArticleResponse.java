package com.zrlog.admin.business.rest.response;

public class CreateOrUpdateArticleResponse {

    private Long logId;
    private int version;
    private com.zrlog.admin.business.content.ArticlePublicationScheduler.Schedule publicationSchedule;
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public com.zrlog.admin.business.content.ArticlePublicationScheduler.Schedule getPublicationSchedule() { return publicationSchedule; }
    public void setPublicationSchedule(com.zrlog.admin.business.content.ArticlePublicationScheduler.Schedule schedule) { this.publicationSchedule = schedule; }



    private Boolean rubbish;

    private Boolean privacy;

    private boolean publicCacheRefreshRequired;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Boolean getRubbish() {
        return rubbish;
    }

    public void setRubbish(Boolean rubbish) {
        this.rubbish = rubbish;
    }

    public Boolean getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Boolean privacy) {
        this.privacy = privacy;
    }

    public boolean isPublicCacheRefreshRequired() {
        return publicCacheRefreshRequired;
    }

    public void setPublicCacheRefreshRequired(boolean publicCacheRefreshRequired) {
        this.publicCacheRefreshRequired = publicCacheRefreshRequired;
    }
}
