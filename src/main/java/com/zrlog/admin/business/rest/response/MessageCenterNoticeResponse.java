package com.zrlog.admin.business.rest.response;

import com.zrlog.common.vo.Version;

public class MessageCenterNoticeResponse {

    private String taskKey;
    private String type;
    private String status;
    private Long updatedAt;
    private Object payload;

    public static MessageCenterNoticeResponse of(String taskKey, String type, String status, Long updatedAt, Object payload) {
        MessageCenterNoticeResponse response = new MessageCenterNoticeResponse();
        response.setTaskKey(taskKey);
        response.setType(type);
        response.setStatus(status);
        response.setUpdatedAt(updatedAt);
        response.setPayload(payload);
        return response;
    }

    public static VersionUpdatePayload versionUpdatePayload(Version version) {
        VersionUpdatePayload payload = new VersionUpdatePayload();
        payload.setVersion(version);
        return payload;
    }

    public static UnreadCommentPayload unreadCommentPayload(Integer count) {
        UnreadCommentPayload payload = new UnreadCommentPayload();
        payload.setCount(count);
        return payload;
    }

    public static WebhookMessagePayload webhookMessagePayload(WebhookMessageNoticeEntry notice) {
        WebhookMessagePayload payload = new WebhookMessagePayload();
        payload.setTitle(notice.getTitle());
        payload.setDescription(notice.getDescription());
        payload.setActionLabel(notice.getActionLabel());
        payload.setActionPath(notice.getActionPath());
        payload.setSource(notice.getSource());
        payload.setClosable(notice.getClosable());
        payload.setPayload(notice.getPayload());
        return payload;
    }

    public static OperationTaskPayload operationPayload(MessageCenterOperationNoticeEntry notice) {
        OperationTaskPayload payload = new OperationTaskPayload();
        payload.setTitle(notice.getTitle());
        payload.setDescription(notice.getDescription());
        payload.setActionLabel(notice.getActionLabel());
        payload.setActionPath(notice.getActionPath());
        payload.setSource(notice.getSource());
        payload.setClosable(notice.getClosable());
        payload.setPayload(notice.getPayload());
        return payload;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public static class VersionUpdatePayload {

        private Version version;

        public Version getVersion() {
            return version;
        }

        public void setVersion(Version version) {
            this.version = version;
        }
    }

    public static class UnreadCommentPayload {

        private Integer count;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

    public static class WebhookMessagePayload {

        private String title;
        private String description;
        private String actionLabel;
        private String actionPath;
        private String source;
        private Boolean closable;
        private Object payload;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public void setActionLabel(String actionLabel) {
            this.actionLabel = actionLabel;
        }

        public String getActionPath() {
            return actionPath;
        }

        public void setActionPath(String actionPath) {
            this.actionPath = actionPath;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Boolean getClosable() {
            return closable;
        }

        public void setClosable(Boolean closable) {
            this.closable = closable;
        }

        public Object getPayload() {
            return payload;
        }

        public void setPayload(Object payload) {
            this.payload = payload;
        }
    }

    public static class OperationTaskPayload {

        private String title;
        private String description;
        private String actionLabel;
        private String actionPath;
        private String source;
        private Boolean closable;
        private Object payload;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public void setActionLabel(String actionLabel) {
            this.actionLabel = actionLabel;
        }

        public String getActionPath() {
            return actionPath;
        }

        public void setActionPath(String actionPath) {
            this.actionPath = actionPath;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public Boolean getClosable() {
            return closable;
        }

        public void setClosable(Boolean closable) {
            this.closable = closable;
        }

        public Object getPayload() {
            return payload;
        }

        public void setPayload(Object payload) {
            this.payload = payload;
        }
    }
}
