package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class AIResponseEntry {

    private String type;
    private List<AIContentEntry> content = new ArrayList<>();
    private String status;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<AIContentEntry> getContent() {
        return content;
    }

    public void setContent(List<AIContentEntry> content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static class AIContentEntry {

        public AIContentEntry() {
        }

        public AIContentEntry(String role, String content) {
            this.role = role;
            this.content = content;
        }

        private String role;
        private String content;
        private String messageId;
        private String messageType;
        private ArticleContextMeta contextMeta;
        private String tool;
        private Object payload;
        private String provider;
        private String model;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public ArticleContextMeta getContextMeta() {
            return contextMeta;
        }

        public void setContextMeta(ArticleContextMeta contextMeta) {
            this.contextMeta = contextMeta;
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

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public static class ArticleContextMeta {

            private String title;
            private Integer articleVersion;
            private Integer markdownLength;
            private Long createdAt;

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public Integer getArticleVersion() {
                return articleVersion;
            }

            public void setArticleVersion(Integer articleVersion) {
                this.articleVersion = articleVersion;
            }

            public Integer getMarkdownLength() {
                return markdownLength;
            }

            public void setMarkdownLength(Integer markdownLength) {
                this.markdownLength = markdownLength;
            }

            public Long getCreatedAt() {
                return createdAt;
            }

            public void setCreatedAt(Long createdAt) {
                this.createdAt = createdAt;
            }
        }
    }

}
