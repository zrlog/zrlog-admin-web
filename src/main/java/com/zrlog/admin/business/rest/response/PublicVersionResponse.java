package com.zrlog.admin.business.rest.response;

public class PublicVersionResponse {

    private String buildId;
    private String message;

    public PublicVersionResponse() {
    }


    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
