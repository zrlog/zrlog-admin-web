package com.zrlog.admin.business.rest.response;

public class WebhookConfigResponse {

    private Boolean enabled;
    private Boolean hasToken;
    private String tokenPreview;
    private Long tokenUpdatedAt;
    private String endpoint;
    private String tokenHeader;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getHasToken() {
        return hasToken;
    }

    public void setHasToken(Boolean hasToken) {
        this.hasToken = hasToken;
    }

    public String getTokenPreview() {
        return tokenPreview;
    }

    public void setTokenPreview(String tokenPreview) {
        this.tokenPreview = tokenPreview;
    }

    public Long getTokenUpdatedAt() {
        return tokenUpdatedAt;
    }

    public void setTokenUpdatedAt(Long tokenUpdatedAt) {
        this.tokenUpdatedAt = tokenUpdatedAt;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }
}
