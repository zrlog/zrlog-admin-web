package com.zrlog.admin.business.rest.response;

public class FirstUseChecklistResponse {

    private int version;
    private String status;

    public FirstUseChecklistResponse() {
    }

    public FirstUseChecklistResponse(int version, String status) {
        this.version = version;
        this.status = status;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
