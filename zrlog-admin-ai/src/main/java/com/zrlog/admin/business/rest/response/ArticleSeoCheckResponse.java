package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticleSeoCheckResponse {

    private int score;
    private String summary;
    private List<SeoItem> items = new ArrayList<>();

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<SeoItem> getItems() {
        return items;
    }

    public void setItems(List<SeoItem> items) {
        this.items = items;
    }

    public static class SeoItem {

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
