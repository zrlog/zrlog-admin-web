package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.ai.model.AIFileModels;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.model.WebSite;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Account-scoped conversation storage, including legacy history migration. */
public class AIConversationService {
    private final SecurityStore store;
    private final WebsiteKvService websiteKvService;
    private final jakarta.inject.Provider<WebSite> websites;
    private final AIFileService files;
    private final AIApprovalStore approvals;

    private final Integer conversationUserId;

    @Inject
    public AIConversationService(
            SecurityStore store,
            WebsiteKvService websiteKvService,
            jakarta.inject.Provider<WebSite> websites,
            AIFileService files,
            AIApprovalStore approvals) {
        this.store = store;
        this.websiteKvService = websiteKvService;
        this.websites = websites;
        this.files = files;
        this.approvals = approvals;
        this.conversationUserId = null;
    }

    private AIConversationService(
            Integer userId,
            SecurityStore store,
            WebsiteKvService websiteKvService,
            jakarta.inject.Provider<WebSite> websites,
            AIFileService files,
            AIApprovalStore approvals) {
        this.store = store;
        this.websiteKvService = websiteKvService;
        this.websites = websites;
        this.files = files;
        this.approvals = approvals;
        this.conversationUserId = userId;
    }

    /** Bind trusted ownership explicitly before reading, writing or dispatching work. */
    public AIConversationService captureAccount(int userId) {
        if (userId <= 0) throw new PermissionErrorException();
        return new AIConversationService(userId, store, websiteKvService, websites, files, approvals);
    }

    private Integer conversationUserId() {
        if (conversationUserId == null) throw new PermissionErrorException();
        return conversationUserId;
    }

    private String conversationKey(Long articleId) {
        Integer userId = conversationUserId();
        if (userId == null) throw new PermissionErrorException();
        return "ai_chat_message_u" + userId + "_" + articleId;
    }

    private static final long DRAFT_ARTICLE_ID = 0L;
    static String buildCacheKey(Long articleId) {
        return "ai_chat_message_" + articleId;
    }

    public boolean migrateDraftAIMessageToArticle(Long articleId) throws SQLException {
        return migrateDraftAIMessageToArticle(articleId, DRAFT_ARTICLE_ID);
    }

    public boolean migrateDraftAIMessageToArticle(Long articleId, Long draftId) throws SQLException {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        // Persist stable IDs before copying, so retrying an interrupted migration is idempotent.
        AtomicReference<List<AIResponseEntry.AIContentEntry>> snapshot = new AtomicReference<>();
        if (!mutateMessages(draftId, messages -> {
            if (messages.isEmpty()) return null;
            snapshot.set(messages);
            return messages;
        })) return false;
        List<AIResponseEntry.AIContentEntry> entries = snapshot.get();
        Set<String> migrated = new HashSet<>();
        for (AIResponseEntry.AIContentEntry entry : entries) migrated.add(entry.getMessageId());
        if (!mutateMessages(articleId, messages -> {
            Set<String> existing = new HashSet<>();
            for (AIResponseEntry.AIContentEntry entry : messages) existing.add(entry.getMessageId());
            for (AIResponseEntry.AIContentEntry entry : entries) if (existing.add(entry.getMessageId())) messages.add(entry);
            return messages;
        })) return false;
        return mutateMessages(draftId, messages -> {
            messages.removeIf(entry -> migrated.contains(entry.getMessageId()));
            return messages;
        });
    }

    public boolean removeAIMessage(Long articleId) {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        try {
            // Article deletion is already authorized by AdminArticleService.
            this.websites
                    .get()
                    .execute(
                            "update website set value=null,remark=? where name like ? escape '!'",
                            UUID.randomUUID().toString(),
                            "ai!_chat!_message!_u%!_" + articleId);
            return this.websiteKvService.remove(buildCacheKey(articleId));
        } catch (SQLException e) { throw new IllegalStateException("Unable to remove article AI messages", e); }
    }

    public boolean clearAIMessage(Long articleId) {
        if (articleId == null || (articleId < DRAFT_ARTICLE_ID && articleId != -(long) Objects.requireNonNullElse(conversationUserId(), 0))) {
            return false;
        }
        try {
            AIApprovalStore checkpoints = this.approvals;
            AIChatModels.Run run = checkpoints.read(conversationUserId(), Math.max(0, articleId));
            List<AIFileModels.Attachment> files = new ArrayList<>();
            for (var entry : getAiMessageInfoByArticleId(articleId).getAiMessages()) {
                if (entry.getAttachments() != null) files.addAll(entry.getAttachments());
            }
            if (run != null) {
                String status = AIApprovalStore.view(run).status;
                if ("running".equals(status) || "executing".equals(status)) return false;
                if (run.attachments != null) files.addAll(run.attachments);
                run.attachments = List.of(); run.messages.clear();
                run.answer = null; run.approval = null; run.interaction = null; run.skillMessages.clear(); checkpoints.save(run, "cancelled");
            }
            if (!mutateMessages(articleId, messages -> new ArrayList<>())) return false;
            // A draft checkpoint may still reference files moved into a saved article.
            Set<String> retained = referencedFileIds();
            files.removeIf(file -> retained.contains(file.id));
            this.files.remove(conversationUserId(), files);
            return true;
        } catch (SQLException | java.io.IOException | AIApprovalStore.Changed e) { return false; }
    }

    private Set<String> referencedFileIds() throws SQLException {
        SecurityStore db = this.store;
        List<Map<String, Object>> rows = db.withSession(c -> db.list(c,
                "select name,value from website where value is not null and (name like ? escape '!' or name like ? escape '!')",
                "ai!_chat!_message!_u" + conversationUserId() + "!_%", "ai!_pending!_u" + conversationUserId() + "!_%"));
        Set<String> ids = new HashSet<>();
        Gson json = new Gson();
        for (var row : rows) {
            String value = Objects.toString(row.get("value"), "");
            if (value.isBlank()) continue;
            if (row.get("name").toString().startsWith("ai_chat_message_")) {
                var entries = json.fromJson(value, AIResponseEntry.AIContentEntry[].class);
                if (entries != null) for (var entry : entries) if (entry.getAttachments() != null)
                    for (var file : entry.getAttachments()) ids.add(file.id);
            } else {
                var checkpoint = json.fromJson(value, AIChatModels.Run.class);
                if (AIApprovalStore.active(checkpoint)) for (var message : checkpoint.messages) if (message.attachments != null)
                    for (var file : message.attachments) ids.add(file.id);
            }
        }
        return ids;
    }

    public ArticleAIMessageExportResponse exportAIMessage(Long articleId) {
        AIWebSiteInfoWithAIMessages info = getAiMessageInfoByArticleId(articleId);
        ArticleAIMessageExportResponse response = new ArticleAIMessageExportResponse();
        response.setArticleId(articleId);
        response.setDraft(articleId != null && articleId <= DRAFT_ARTICLE_ID);
        response.setExportedAt(System.currentTimeMillis());
        response.setMessages(info.getAiMessages());
        response.setMessageCount(info.getAiMessages().size());
        return response;
    }

    public AIWebSiteInfoWithAIMessages getAiMessageInfoByArticleId(Long articleId) {
        String aiMessageKey = conversationKey(articleId);
        List<String> names = new ArrayList<>(AIConfigService.AI_WEBSITE_INFO_KEYS);
        names.add(aiMessageKey);
        if (!aiMessageKey.equals(buildCacheKey(articleId))) names.add(buildCacheKey(articleId));
        Map<String, Object> map = websites.get().getWebSiteByNameIn(names);
        AIWebSiteInfoWithAIMessages info = AIConfigService.normalizeAIWebSiteInfo(
                ResultBeanUtils.convert(map, AIWebSiteInfoWithAIMessages.class));
        fillAiMessages(info, map, map.get(aiMessageKey) == null ? buildCacheKey(articleId) : aiMessageKey);
        return info;
    }

    void fillAiMessages(AIWebSiteInfoWithAIMessages info, Map<String, Object> map, String aiMessageKey) {
        String messages = (String) map.get(aiMessageKey);
        if (StringUtils.isNotEmpty(messages)) {
            AIResponseEntry.AIContentEntry[] aiContentEntries = new Gson().fromJson(messages, AIResponseEntry.AIContentEntry[].class);
            info.setAiMessages(aiContentEntries == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(aiContentEntries)));
        } else {
            info.setAiMessages(new ArrayList<>());
        }
    }

    /** Compare-and-set the website row; retries merge against the latest committed conversation. */
    private boolean mutateMessages(Long articleId, UnaryOperator<List<AIResponseEntry.AIContentEntry>> change) throws SQLException {
        SecurityStore db = this.store;
        String key = conversationKey(articleId);
        Gson json = new Gson();
        for (int attempt = 0; attempt < 8; attempt++) {
            Map<String, Object> row = db.withSession(c -> db.one(c, "select value,remark from website where name=?", key));
            String old = row == null ? null : Objects.toString(row.get("value"), null);
            List<AIResponseEntry.AIContentEntry> current = old == null || old.isBlank()
                    ? new ArrayList<>(getAiMessageInfoByArticleId(articleId).getAiMessages())
                    : new ArrayList<>(Arrays.asList(json.fromJson(old, AIResponseEntry.AIContentEntry[].class)));
            List<AIResponseEntry.AIContentEntry> updated = change.apply(current);
            if (updated == null) return false;
            fillMissingMessageIds(updated);
            String value = json.toJson(updated), revision = UUID.randomUUID().toString();
            if (row == null) {
                try {
                    db.withSession(c -> db.update(c, "insert into website(name,value,remark) values(?,?,?)", key, value, revision));
                    return true;
                } catch (SQLException e) {
                    if (db.withSession(c -> db.one(c, "select name from website where name=?", key)) == null) throw e;
                }
            } else {
                Object previous = row.get("remark");
                int count = previous == null
                        ? db.withSession(c -> db.update(c, "update website set value=?,remark=? where name=? and remark is null", value, revision, key))
                        : db.withSession(c -> db.update(c, "update website set value=?,remark=? where name=? and remark=?", value, revision, key, previous));
                if (count == 1) return true;
            }
        }
        throw new SQLException("Conversation changed concurrently");
    }

    public boolean saveAIMessage(List<AIResponseEntry.AIContentEntry> messages, Long articleId) throws SQLException {
        return mutateMessages(articleId, ignored -> new ArrayList<>(messages));
    }

    public boolean appendAIMessageEntries(List<AIResponseEntry.AIContentEntry> entries, Long articleId) throws SQLException {
        fillMissingMessageIds(entries);
        String prompt = getAiMessageInfoByArticleId(articleId).getAi_prompt();
        return mutateMessages(articleId, messages -> {
            ensureSystemMessage(messages, prompt);
            fillMissingMessageIds(messages);
            Set<String> ids = new HashSet<>();
            for (AIResponseEntry.AIContentEntry entry : messages) ids.add(entry.getMessageId());
            for (AIResponseEntry.AIContentEntry entry : entries) if (ids.add(entry.getMessageId())) messages.add(entry);
            return messages;
        });
    }

    public boolean updateAIMessagePayload(Long articleId, String messageId, String tool, Object payload) throws SQLException {
        return mutateMessages(articleId, messages -> {
            for (AIResponseEntry.AIContentEntry entry : messages) if (Objects.equals(messageId, entry.getMessageId())) {
                entry.setTool(tool); entry.setPayload(payload); return messages;
            }
            return null;
        });
    }

    public void ensureSystemMessage(List<AIResponseEntry.AIContentEntry> messages, String aiPrompt) {
        boolean hasSystemMessage = messages.stream().anyMatch(message -> Objects.equals(message.getRole(), "system"));
        if (!hasSystemMessage) {
            messages.add(0, new AIResponseEntry.AIContentEntry("system", emptyToBlank(aiPrompt)));
        }
    }

    String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    void fillMissingMessageIds(List<AIResponseEntry.AIContentEntry> messages) {
        for (AIResponseEntry.AIContentEntry message : messages) {
            if (StringUtils.isEmpty(message.getMessageId())) {
                message.setMessageId(UUID.randomUUID().toString());
            }
        }
    }

}
