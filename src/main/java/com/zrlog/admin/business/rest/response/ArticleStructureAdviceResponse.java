package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticleStructureAdviceResponse {

    private String summary;
    private List<StructureItem> items = new ArrayList<>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<StructureItem> getItems() {
        return items;
    }

    public void setItems(List<StructureItem> items) {
        this.items = items;
    }

    public static class StructureItem {

        private String name;
        private String status;
        private String suggestion;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
}
