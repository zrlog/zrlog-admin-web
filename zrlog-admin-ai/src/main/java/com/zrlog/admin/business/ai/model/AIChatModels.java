package com.zrlog.admin.business.ai.model;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Source;
import java.util.*;

public final class AIChatModels {
    private AIChatModels() { }
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
