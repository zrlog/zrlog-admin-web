package com.zrlog.admin.business.ai.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class AIProviderRequests {

    private AIProviderRequests() {
    }

    public static class CompletionRequest {

        private List<Message> messages;
        private String model;
        private boolean stream;
        public List<Tool> tools;
        public String tool_choice;
        @SerializedName("max_completion_tokens")
        private Integer maxCompletionTokens;
        @SerializedName("max_tokens")
        private Integer maxTokens;
        @SerializedName("enable_thinking")
        private Boolean enableThinking;

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }

        public Integer getMaxCompletionTokens() {
            return maxCompletionTokens;
        }

        public void setMaxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }
    }

    public static class Message {

        private String role;
        private String content;
        @SerializedName("tool_calls")
        public List<ToolCall> toolCalls;
        @SerializedName("tool_call_id")
        public String toolCallId;
        @SerializedName("reasoning_content")
        public String reasoningContent;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

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
    }

    public static class Tool {
        public String type = "function";
        public Function function;
    }
    public static class Function {
        public String name;
        public String description;
        public com.google.gson.JsonObject parameters;
    }
    public static class ToolCall {
        public String id;
        public String type;
        public FunctionCall function;
        public ExtraContent extra_content;
    }
    public static class ExtraContent { public GoogleContent google; }
    public static class GoogleContent { public String thought_signature; }
    public static class FunctionCall {
        public String name;
        public String arguments;
    }

    public static class ImageGenerationRequest {

        private String model;
        private String prompt;
        private int n;
        private String size;

        public ImageGenerationRequest() {
        }

        public ImageGenerationRequest(String model, String prompt, int n, String size) {
            this.model = model;
            this.prompt = prompt;
            this.n = n;
            this.size = size;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            this.n = n;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }
    }
}
