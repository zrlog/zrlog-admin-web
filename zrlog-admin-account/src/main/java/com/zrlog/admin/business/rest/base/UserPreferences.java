package com.zrlog.admin.business.rest.base;

/** Nullable fields are personal overrides; null inherits the site default. */
public class UserPreferences {
    public String language;
    public Appearance appearance;
    public Integer articlePageSize;
    public Editor editor;
    public Assistant assistant;
    public Session session;
    public ArticleList articleList;

    public static class Appearance {
        public String theme;
        public Boolean darkMode;
        public Boolean compactMode;
        public String colorPrimary;
    }

    public static class Assistant {
        public String knowledgeScope;
    }

    public static class ArticleList {
        public String sort;
        public String status;
        public java.util.List<String> columns;
    }

    public static class Session {
        public Long timeoutMinutes;
    }

    public static class Editor {
        public Boolean linkPreviewEnabled;
        public Boolean publishCheckEnabled;
        public Long autoDigestLength;
        public String coverAspectRatio;
        public Long autoSaveInterval;
    }
}
