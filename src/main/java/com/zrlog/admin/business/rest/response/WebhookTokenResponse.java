package com.zrlog.admin.business.rest.response;

public class WebhookTokenResponse {

    private String token;
    private WebhookConfigResponse config;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public WebhookConfigResponse getConfig() {
        return config;
    }

    public void setConfig(WebhookConfigResponse config) {
        this.config = config;
    }
}
