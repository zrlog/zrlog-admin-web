package com.zrlog.admin.business.knowledge;

import java.util.ArrayList;
import java.util.List;

public final class ContentToolModels {
    private ContentToolModels() { }
    public static class ArticleDetails {
        public long id;
        public int version;
        public String title, status, alias, digest, keywords, thumbnail, editorType;
        public long typeId;
        public boolean canComment, recommended;
    }
    public static class SavedArticle {
        public long id;
        public int version;
        public String status;
        public String refreshStatus = "not_required";
        public String warning;
    }
    public static class TaxonomyEntry {
        public long id;
        public String name, alias, description;
    }
    public static class TaxonomyList {
        public List<TaxonomyEntry> items = new ArrayList<>();
        public Integer nextOffset;
    }
    public static class Attachment {
        public String url;
        public String filename;
        public int size;
    }
}
