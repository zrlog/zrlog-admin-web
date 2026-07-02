package com.zrlog.admin.business.ai.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class AIProviderResponses {

    private AIProviderResponses() {
    }

    public static class CompletionResponse {

        private List<Choice> choices;
        private JsonElement error;
        private String message;

        public List<Choice> getChoices() {
            return choices;
        }

        public void setChoices(List<Choice> choices) {
            this.choices = choices;
        }

        public JsonElement getError() {
            return error;
        }

        void setError(JsonElement error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class ErrorPayload {

        private String message;
        private String type;
        private String code;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class Choice {

        private Message message;
        private Delta delta;
        @SerializedName(value = "finish_reason", alternate = {"finishReason"})
        private String finishReason;

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public Delta getDelta() {
            return delta;
        }

        public void setDelta(Delta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    public static class Message {

        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class Delta {

        private String content;
        @SerializedName(value = "reasoning_content", alternate = {"reasoningContent"})
        private String reasoningContent;
        private Object reasoning;

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

        public Object getReasoning() {
            return reasoning;
        }

        public void setReasoning(Object reasoning) {
            this.reasoning = reasoning;
        }
    }

    public static class ImageGenerationResponse {

        private List<ImageData> data;

        public List<ImageData> getData() {
            return data;
        }

        public void setData(List<ImageData> data) {
            this.data = data;
        }
    }

    public static class ImageData {

        @SerializedName("b64_json")
        private String b64Json;
        private String url;

        public String getB64Json() {
            return b64Json;
        }

        public void setB64Json(String b64Json) {
            this.b64Json = b64Json;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
