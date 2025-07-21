package com.zrlog.admin.business.rest.response;

public class HealthCheckSuggestionResponse {

    private final String key;
    private final String actionUri;

    public HealthCheckSuggestionResponse(String key, String actionUri) {
        this.key = key;
        this.actionUri = actionUri;
    }

    public String getKey() {
        return key;
    }

    public String getActionUri() {
        return actionUri;
    }
}
