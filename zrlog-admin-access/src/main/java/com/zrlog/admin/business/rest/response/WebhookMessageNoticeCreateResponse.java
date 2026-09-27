package com.zrlog.admin.business.rest.response;

public class WebhookMessageNoticeCreateResponse {

    private String taskKey;
    private Long updatedAt;

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
