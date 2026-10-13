package com.zrlog.admin.business.ai.model;

import java.util.List;

public final class AIFileModels {
    private AIFileModels() { }

    public static class Input {
        public String name;
        public String data;
    }

    public static class Attachment {
        public String id;
        public String name;
        public String mimeType;
        public int size;
        public long expiresAt;
    }

    public static class Capabilities {
        public List<String> extensions = List.of();
        public int maxFiles = 4;
        public int maxFileBytes = 4 * 1024 * 1024;
        public int maxTotalBytes = 8 * 1024 * 1024;
    }
}
