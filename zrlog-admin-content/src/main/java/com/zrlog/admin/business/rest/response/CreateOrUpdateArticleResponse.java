package com.zrlog.admin.business.rest.response;

public class CreateOrUpdateArticleResponse {

    private Long logId;


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
