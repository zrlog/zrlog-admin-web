package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticleProofreadResponse {

    private String summary;
    private List<ProofreadItem> items = new ArrayList<>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ProofreadItem> getItems() {
        return items;
    }

    public void setItems(List<ProofreadItem> items) {
        this.items = items;
    }

    public static class ProofreadItem {

        private String original;
        private String issue;
        private String suggestion;

        public String getOriginal() {
            return original;
        }

        public void setOriginal(String original) {
            this.original = original;
        }

        public String getIssue() {
            return issue;
        }

        public void setIssue(String issue) {
            this.issue = issue;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
}
