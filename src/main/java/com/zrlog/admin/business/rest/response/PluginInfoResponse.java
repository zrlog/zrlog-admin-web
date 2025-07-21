package com.zrlog.admin.business.rest.response;

public class PluginInfoResponse {

    private String includePagePath;

    public PluginInfoResponse() {
    }

    public PluginInfoResponse(String includePagePath) {
        this.includePagePath = includePagePath;
    }

    public String getIncludePagePath() {
        return includePagePath;
    }

    public void setIncludePagePath(String includePagePath) {
        this.includePagePath = includePagePath;
    }
}
