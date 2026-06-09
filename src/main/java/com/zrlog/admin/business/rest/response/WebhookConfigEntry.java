package com.zrlog.admin.business.rest.response;

public class WebhookConfigEntry {

    private Boolean enabled;
    private String tokenHash;
    private String tokenPreview;
    private Long tokenUpdatedAt;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
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
}
