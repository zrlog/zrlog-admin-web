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
    public static class ApprovalRequest implements Validator {
        public long articleId;
        public String runId;
        public String approvalId;
        public String decision;
        public void doValid() {
            if (articleId < 0 || !uuid(runId) || !uuid(approvalId)
                    || !("approve".equals(decision) || "reject".equals(decision))) throw new ArgsException();
        }
        private static boolean uuid(String value) {
            return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        }
    }
    public static class Approval {
        public String id;
        public String tool;
        public Long articleId;
        public Integer version;
        public String title;
        public boolean publicImpact;
        public long expiresAt;
        public List<ApprovalChange> changes = new ArrayList<>();
    }
    public static class ApprovalChange {
        public String field;
        public String before;
        public String after;
        public boolean truncated;
    }
    /** Durable execution checkpoint. Never sent to the browser or stored in public settings. */
    public static class Run {
        public String id = UUID.randomUUID().toString();
        public int userId;
        public int authVersion;
        public long articleId;
        public String input;
        public String language;
        public String preferences;
        public String provider;
        public String model;
        public String revision;
        public String status;
        public long updatedAt;
        public int metadataIndex;
        public int round;
        public int calls;
        public int nextTool;
        public List<String> allowedTools = new ArrayList<>();
        public List<AIProviderRequests.Message> messages = new ArrayList<>();
        public List<AIProviderRequests.ToolCall> toolCalls = new ArrayList<>();
        public List<Source> sources = new ArrayList<>();
        public String reasoning = "";
        public Approval approval;
        public List<Event> articleUpdates = new ArrayList<>();
        public Event answer;
        public String error;
    }
    public static class RunView {
        public String runId;
        public long articleId;
        public String input;
        public String status;
        public Approval approval;
        public String error;
        public Event answer;
        public List<Event> articleUpdates;
    }
    public static class CurrentArticleContext {
        public long articleId;
        public String title;
        public String status;
        public Integer version;
        public Long typeId;
    }
    public static class Event {
        public String type;
        public String runId;
        public RunView run;
        public String tool;
        public String content;
        public String reasoningContent;
        public String error;
        public Long articleId;
        public Integer version;
        public Boolean created;
        public List<com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry> messages;
        public List<Source> sources;
        public Event(String type) { this.type = type; }
    }
}
