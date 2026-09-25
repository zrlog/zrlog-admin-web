package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonObject;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import java.util.*;

public final class KnowledgeModels {
    private KnowledgeModels() { }
    public static class Options {
        public boolean allArticles;
        public boolean drafts;
        public boolean privateArticles;
        public Set<String> scopes() {
            Set<String> result = new LinkedHashSet<>(Set.of("articles:read"));
            if (allArticles) result.add("articles:all");
            if (drafts) result.add("articles:read_drafts");
            if (privateArticles) result.add("articles:read_private");
            return result;
        }
    }
    public static class ChatRequest implements Validator {
        public String input;
        public long articleId;
        public boolean includeArticleContext = true;
        public List<ChatMessage> history = new ArrayList<>();
        public void doValid() {
            if (articleId < 0 || input == null || input.trim().isEmpty() || input.length() > 8000
                    || history == null || history.size() > 12) throw new ArgsException();
            int size = 0;
            for (ChatMessage m : history) {
                if (m == null || !("user".equals(m.role) || "assistant".equals(m.role)) || m.content == null) throw new ArgsException();
                size += m.content.length();
            }
            if (size > 32000) throw new ArgsException();
        }
    }
    public static class ChatMessage { public String role; public String content; }
    public static class Source {
        public long id;
        public String title;
        public String url;
        public boolean draft;
        public boolean privateArticle;
        public String updatedAt;
    }
    public static class SearchHit extends Source { public String excerpt; }
    public static class SearchResult {
        public List<SearchHit> articles = new ArrayList<>();
        public Integer nextOffset;
    }
    public static class ArticleResult {
        public Source source;
        public String content;
        public String format;
        public int offset;
        public Integer nextOffset;
    }
    public static class Tool {
        public String name;
        public String description;
        public JsonObject inputSchema;
        public Annotations annotations = new Annotations();
    }
    public static class Annotations {
        public boolean readOnlyHint = true;
        public boolean destructiveHint = false;
        public boolean idempotentHint = true;
        public boolean openWorldHint = false;
    }
    public static class ToolError { public String error; public ToolError(String error) { this.error = error; } }
    public static class Event {
        public String type;
        public String tool;
        public String content;
        public String reasoningContent;
        public String error;
        public List<com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry> messages;
        public List<Source> sources;
        public Event(String type) { this.type = type; }
    }
}
