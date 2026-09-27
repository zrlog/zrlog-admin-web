package com.zrlog.admin.business.ai.dto;

public final class AIStreamPayloads {

    private AIStreamPayloads() {
    }

    public static class Chunk {

        private String tool;
        private Object payload;
        private String content;
        private String reasoningContent;
        private String messageId;

        public static Chunk content(String content) {
            Chunk chunk = new Chunk();
            chunk.setContent(content);
            return chunk;
        }

        public static Chunk reasoning(String reasoningContent) {
            Chunk chunk = new Chunk();
            chunk.setReasoningContent(reasoningContent);
            return chunk;
        }

        public static Chunk tool(String tool, Object payload, String messageId) {
            Chunk chunk = new Chunk();
            chunk.setTool(tool);
            chunk.setPayload(payload);
            chunk.setContent("");
            chunk.setMessageId(messageId);
            return chunk;
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

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getReasoningContent() {
            return reasoningContent;
        }

        public void setReasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }
    }

    public static class ErrorPayload {

        private String message;
        private String errorType;
        private String finishReason;
        private Integer continuationRounds;
        private String provider;
        private String model;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getErrorType() {
            return errorType;
        }

        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public Integer getContinuationRounds() {
            return continuationRounds;
        }

        public void setContinuationRounds(Integer continuationRounds) {
            this.continuationRounds = continuationRounds;
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
    }

    public static class CoverPayload {

        private String url;

        public CoverPayload() {
        }

        public CoverPayload(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
