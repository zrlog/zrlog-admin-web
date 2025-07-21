package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.rest.request.UpdateArticleRequest;

public class LoadEditArticleResponse extends UpdateArticleRequest {

    private String previewUrl;
    private Long lastUpdateDate;

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Integer getId() {
        return logId;
    }

    public Long getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Long lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
}
