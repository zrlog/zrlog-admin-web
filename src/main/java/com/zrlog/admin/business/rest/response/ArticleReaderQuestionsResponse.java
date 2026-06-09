package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class ArticleReaderQuestionsResponse {

    private String summary;
    private List<ReaderQuestionItem> items = new ArrayList<>();

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<ReaderQuestionItem> getItems() {
        return items;
    }

    public void setItems(List<ReaderQuestionItem> items) {
        this.items = items;
    }

    public static class ReaderQuestionItem {

        private String question;
        private String reason;
        private String suggestion;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
}
