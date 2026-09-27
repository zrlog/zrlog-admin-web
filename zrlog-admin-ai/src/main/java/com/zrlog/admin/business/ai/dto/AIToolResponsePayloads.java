package com.zrlog.admin.business.ai.dto;

import com.google.gson.JsonElement;

import java.util.List;

public final class AIToolResponsePayloads {

    private AIToolResponsePayloads() {
    }

    public static class Titles {

        private List<String> titles;

        public List<String> getTitles() {
            return titles;
        }

        public void setTitles(List<String> titles) {
            this.titles = titles;
        }
    }

    public static class Tags {

        private List<String> tags;

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    public static class Markdown {

        private String summary;
        private String markdown;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getMarkdown() {
            return markdown;
        }

        public void setMarkdown(String markdown) {
            this.markdown = markdown;
        }
    }

    public static class ArticleScore {

        private Object score;
        private String summary;
        private JsonElement items;

        public Object getScore() {
            return score;
        }

        public void setScore(Object score) {
            this.score = score;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JsonElement getItems() {
            return items;
        }

        void setItems(JsonElement items) {
            this.items = items;
        }
    }

    public static class ArticleScoreItem {

        private String name;
        private Object score;
        private String suggestion;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getScore() {
            return score;
        }

        public void setScore(Object score) {
            this.score = score;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }

    public static class ArticleSeo {

        private Object score;
        private String summary;
        private JsonElement items;

        public Object getScore() {
            return score;
        }

        public void setScore(Object score) {
            this.score = score;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JsonElement getItems() {
            return items;
        }

        void setItems(JsonElement items) {
            this.items = items;
        }
    }

    public static class ArticleSeoItem {

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

    public static class ArticleProofread {

        private String summary;
        private JsonElement items;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JsonElement getItems() {
            return items;
        }

        void setItems(JsonElement items) {
            this.items = items;
        }
    }

    public static class ArticleProofreadItem {

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

    public static class ArticleStructure {

        private String summary;
        private JsonElement items;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JsonElement getItems() {
            return items;
        }

        void setItems(JsonElement items) {
            this.items = items;
        }
    }

    public static class ArticleStructureItem {

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

    public static class ReaderQuestions {

        private String summary;
        private JsonElement items;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JsonElement getItems() {
            return items;
        }

        void setItems(JsonElement items) {
            this.items = items;
        }
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
