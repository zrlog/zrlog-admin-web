package com.zrlog.admin.business.rest.response;

public final class AdminSsePayloads {

    private AdminSsePayloads() {
    }

    public static Message message(String message) {
        return new Message(message);
    }

    public static Tool tool(String tool) {
        return new Tool(tool);
    }

    public static Error error(int error, String message) {
        return new Error(error, message);
    }

    public static class Message {

        private String message;

        public Message() {
        }

        public Message(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class Tool {

        private String tool;

        public Tool() {
        }

        public Tool(String tool) {
            this.tool = tool;
        }

        public String getTool() {
            return tool;
        }

        public void setTool(String tool) {
            this.tool = tool;
        }
    }

    public static class Error {

        private int error;
        private String message;

        public Error() {
        }

        public Error(int error, String message) {
            this.error = error;
            this.message = message;
        }

        public int getError() {
            return error;
        }

        public void setError(int error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
