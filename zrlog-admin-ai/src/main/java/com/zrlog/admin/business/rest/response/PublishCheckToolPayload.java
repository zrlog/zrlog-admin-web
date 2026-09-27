package com.zrlog.admin.business.rest.response;

public class PublishCheckToolPayload {

    private String tool;
    private Object payload;

    public PublishCheckToolPayload() {
    }

    public PublishCheckToolPayload(String tool, Object payload) {
        this.tool = tool;
        this.payload = payload;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
