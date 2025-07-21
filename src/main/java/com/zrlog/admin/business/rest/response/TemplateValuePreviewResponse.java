package com.zrlog.admin.business.rest.response;

public class TemplateValuePreviewResponse {

    private String previewValue;

    public TemplateValuePreviewResponse() {
    }

    public TemplateValuePreviewResponse(String previewValue) {
        this.previewValue = previewValue;
    }

    public String getPreviewValue() {
        return previewValue;
    }

    public void setPreviewValue(String previewValue) {
        this.previewValue = previewValue;
    }
}
