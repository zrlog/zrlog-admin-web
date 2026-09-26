package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonObject;
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

}
