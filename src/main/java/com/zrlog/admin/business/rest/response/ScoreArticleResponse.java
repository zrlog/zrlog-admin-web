package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ScoreArticleResponse {

    private int score;
    private String summary;
    private List<ScoreItem> items = new ArrayList<>();

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

    public List<ScoreItem> getItems() {
        return items;
    }

    public void setItems(List<ScoreItem> items) {
        this.items = items;
    }

    public static class ScoreItem {

        private String name;
        private int score;
        private String suggestion;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
}
