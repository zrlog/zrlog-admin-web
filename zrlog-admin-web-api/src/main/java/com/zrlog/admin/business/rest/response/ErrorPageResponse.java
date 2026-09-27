package com.zrlog.admin.business.rest.response;

public class ErrorPageResponse {

    private String message;

    public ErrorPageResponse() {
    }

    public ErrorPageResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
