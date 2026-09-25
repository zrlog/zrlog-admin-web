package com.zrlog.admin.business.rest.base;

/** Nullable fields are personal overrides; null inherits the site default. */
public class UserPreferences {
    public String language;
    public Appearance appearance;
    public Integer articlePageSize;
    public Editor editor;
    public Assistant assistant;

    public static class Appearance {
        public String theme;
        public Boolean darkMode;
        public Boolean compactMode;
        public String colorPrimary;
    }

    public static class Assistant {
        public String knowledgeScope;
    }

    public static class Editor {
        public Long autoSaveInterval;
    }
}
