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

}
