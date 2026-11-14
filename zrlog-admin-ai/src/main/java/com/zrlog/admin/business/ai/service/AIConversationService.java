package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.model.AIChatModels;
import com.zrlog.admin.business.ai.model.AIFileModels;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Account-scoped conversation storage, including legacy history migration. */
public class AIConversationService {
    private static final long DRAFT_ARTICLE_ID = 0L;
    private final SecurityStore store;
    private final WebsiteKvService websiteKvService;
    private final jakarta.inject.Provider<WebSiteRepository> websites;
    private final AIFileService files;
    private final AIApprovalStore approvals;
    private final Integer conversationUserId;
    private final String taskId;

    @Inject
    public AIConversationService(
            SecurityStore store,
            WebsiteKvService websiteKvService,
            jakarta.inject.Provider<WebSiteRepository> websites,
            AIFileService files,
            AIApprovalStore approvals) {
        this.store = store;
        this.websiteKvService = websiteKvService;
        this.websites = websites;
        this.files = files;
        this.approvals = approvals;
        this.conversationUserId = null;
        this.taskId = null;
    }

    private AIConversationService(
            Integer userId,
            SecurityStore store,
            WebsiteKvService websiteKvService,
            jakarta.inject.Provider<WebSiteRepository> websites,
            AIFileService files,
            AIApprovalStore approvals,
            String taskId) {
        this.store = store;
        this.websiteKvService = websiteKvService;
        this.websites = websites;
        this.files = files;
        this.approvals = approvals;
        this.conversationUserId = userId;
        this.taskId = taskId;
    }

    static String buildCacheKey(Long articleId) {
        return "ai_chat_message_" + articleId;
    }

    /** Bind trusted ownership explicitly before reading, writing or dispatching work. */
    public AIConversationService captureAccount(int userId) {
        if (userId <= 0) throw new PermissionErrorException();
        return new AIConversationService(
                userId, store, websiteKvService, websites, files, approvals, taskId);
    }

    public AIConversationService captureTask(String taskId) {
        AIConversationScope.key(null, taskId);
        return new AIConversationService(
                conversationUserId(), store, websiteKvService, websites, files, approvals, taskId);
    }

    private Integer conversationUserId() {
        if (conversationUserId == null) throw new PermissionErrorException();
        return conversationUserId;
    }

    private String conversationKey(Long articleId) {
        Integer userId = conversationUserId();
        if (userId == null) throw new PermissionErrorException();
        String scope =
                taskId != null || articleId == null
                        ? AIConversationScope.key(articleId, taskId)
                        : articleId.toString();
        return "ai_chat_message_u" + userId + "_" + scope;
    }

    public boolean migrateDraftAIMessageToArticle(Long articleId) throws SQLException {
        return migrateDraftAIMessageToArticle(articleId, DRAFT_ARTICLE_ID);
    }

    public boolean migrateDraftAIMessageToArticle(Long articleId, Long draftId)
            throws SQLException {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        // Persist stable IDs before copying, so retrying an interrupted migration is idempotent.
        AtomicReference<List<AIResponseEntry.AIContentEntry>> snapshot = new AtomicReference<>();
        if (!mutateMessages(
                draftId,
                messages -> {
                    if (messages.isEmpty()) return null;
                    snapshot.set(messages);
                    return messages;
                })) return false;
        List<AIResponseEntry.AIContentEntry> entries = snapshot.get();
        Set<String> migrated = new HashSet<>();
        for (AIResponseEntry.AIContentEntry entry : entries) migrated.add(entry.getMessageId());
        if (!mutateMessages(
                articleId,
                messages -> {
                    Set<String> existing = new HashSet<>();
                    for (AIResponseEntry.AIContentEntry entry : messages)
                        existing.add(entry.getMessageId());
                    for (AIResponseEntry.AIContentEntry entry : entries)
                        if (existing.add(entry.getMessageId())) messages.add(entry);
                    return messages;
                })) return false;
        return mutateMessages(
                draftId,
                messages -> {
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
                    .updateContentWhere(
                            null,
                            UUID.randomUUID().toString(),
                            "name like ? escape '!'",
                            "ai!_chat!_message!_u%!_" + articleId);
            return this.websiteKvService.remove(buildCacheKey(articleId));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to remove article AI messages", e);
        }
    }

    public boolean clearAIMessage(Long articleId) {
        if (articleId != null
                && (articleId < DRAFT_ARTICLE_ID
                        && articleId
                                != -(long) Objects.requireNonNullElse(conversationUserId(), 0))) {
            return false;
        }
        try {
            AIApprovalStore checkpoints = this.approvals;
            AIChatModels.Run run =
                    checkpoints.read(
                            conversationUserId(),
                            articleId == null ? null : Math.max(0, articleId),
                            taskId);
            List<AIFileModels.Attachment> files = new ArrayList<>();
            for (var entry : getAiMessageInfoByArticleId(articleId).getAiMessages()) {
                if (entry.getAttachments() != null) files.addAll(entry.getAttachments());
            }
            if (run != null) {
                String status = AIApprovalStore.view(run).status;
                if ("running".equals(status) || "executing".equals(status)) return false;
                if (run.attachments != null) files.addAll(run.attachments);
                run.attachments = List.of();
                run.messages.clear();
                run.answer = null;
                run.approval = null;
                run.interaction = null;
                run.skillMessages.clear();
                checkpoints.save(run, "cancelled");
            }
            if (!mutateMessages(
                    articleId,
                    messages -> {
                        var cleared = new ArrayList<AIResponseEntry.AIContentEntry>();
                        if (taskId != null) {
                            var marker = feedbackMarker();
                            marker.getTaskFeedbackState().clearedAt = System.currentTimeMillis();
                            cleared.add(marker);
                        }
                        return cleared;
                    })) return false;
            // A draft checkpoint may still reference files moved into a saved article.
            Set<String> retained = referencedFileIds();
            files.removeIf(file -> retained.contains(file.id));
            this.files.remove(conversationUserId(), files);
            return true;
        } catch (SQLException | java.io.IOException | AIApprovalStore.Changed e) {
            return false;
        }
    }

    private Set<String> referencedFileIds() throws SQLException {
        SecurityStore db = this.store;
        List<WebSite> rows =
                db.withSession(
                        c ->
                                db.entities(
                                        c,
                                        WebSite.class,
                                        "WHERE value is not null and (name like ? escape '!' or name like ? escape '!')",
                                        "ai!_chat!_message!_u" + conversationUserId() + "!_%",
                                        "ai!_pending!_u" + conversationUserId() + "!_%"));
        Set<String> ids = new HashSet<>();
        Gson json = new Gson();
        for (var row : rows) {
            String value = Objects.toString(row.getValue(), "");
            if (value.isBlank()) continue;
            if (row.getName().toString().startsWith("ai_chat_message_")) {
                var entries = json.fromJson(value, AIResponseEntry.AIContentEntry[].class);
                if (entries != null)
                    for (var entry : entries)
                        if (entry.getAttachments() != null)
                            for (var file : entry.getAttachments()) ids.add(file.id);
            } else {
                var checkpoint = json.fromJson(value, AIChatModels.Run.class);
                if (AIApprovalStore.active(checkpoint))
                    for (var message : checkpoint.messages)
                        if (message.attachments != null)
                            for (var file : message.attachments) ids.add(file.id);
            }
        }
        return ids;
    }

    public ArticleAIMessageExportResponse exportAIMessage(Long articleId) {
        AIWebSiteInfoWithAIMessages info = getAiMessageInfoByArticleId(articleId);
        ArticleAIMessageExportResponse response = new ArticleAIMessageExportResponse();
        response.setArticleId(articleId);
        response.setTaskId(taskId);
        response.setDraft(articleId != null && articleId <= DRAFT_ARTICLE_ID);
        response.setExportedAt(System.currentTimeMillis());
        response.setMessages(
                info.getAiMessages().stream()
                        .filter(entry -> !"system".equals(entry.getRole()))
                        .collect(java.util.stream.Collectors.toList()));
        response.setMessageCount(response.getMessages().size());
        return response;
    }

    public AIWebSiteInfoWithAIMessages getAiMessageInfoByArticleId(Long articleId) {
        String aiMessageKey = conversationKey(articleId);
        List<String> names = new ArrayList<>(AIConfigService.AI_WEBSITE_INFO_KEYS);
        names.add(aiMessageKey);
        boolean article = articleId != null && taskId == null;
        if (article && !aiMessageKey.equals(buildCacheKey(articleId)))
            names.add(buildCacheKey(articleId));
        Map<String, Object> map = websites.get().getWebSiteByNameIn(names);
        AIWebSiteInfoWithAIMessages info =
                AIConfigService.normalizeAIWebSiteInfo(
                        ResultBeanUtils.convert(map, AIWebSiteInfoWithAIMessages.class));
        fillAiMessages(
                info,
                map,
                article && map.get(aiMessageKey) == null ? buildCacheKey(articleId) : aiMessageKey);
        for (var entry : info.getAiMessages()) TaskResultMessages.localize(entry);
        return info;
    }

    void fillAiMessages(
            AIWebSiteInfoWithAIMessages info, Map<String, Object> map, String aiMessageKey) {
        String messages = (String) map.get(aiMessageKey);
        if (StringUtils.isNotEmpty(messages)) {
            AIResponseEntry.AIContentEntry[] aiContentEntries =
                    new Gson().fromJson(messages, AIResponseEntry.AIContentEntry[].class);
            info.setAiMessages(
                    aiContentEntries == null
                            ? new ArrayList<>()
                            : new ArrayList<>(Arrays.asList(aiContentEntries)));
        } else {
            info.setAiMessages(new ArrayList<>());
        }
    }

    /** Compare-and-set the website row; retries merge against the latest committed conversation. */
    private boolean mutateMessages(
            Long articleId, UnaryOperator<List<AIResponseEntry.AIContentEntry>> change)
            throws SQLException {
        SecurityStore db = this.store;
        String key = conversationKey(articleId);
        Gson json = new Gson();
        for (int attempt = 0; attempt < 8; attempt++) {
            WebSite row =
                    db.withSession(
                            c -> db.firstEntity(c, WebSite.class, "WHERE name=? LIMIT 1", key));
            String old = row == null ? null : Objects.toString(row.getValue(), null);
            List<AIResponseEntry.AIContentEntry> current =
                    old == null || old.isBlank()
                            ? new ArrayList<>(
                                    getAiMessageInfoByArticleId(articleId).getAiMessages())
                            : new ArrayList<>(
                                    Arrays.asList(
                                            json.fromJson(
                                                    old, AIResponseEntry.AIContentEntry[].class)));
            List<AIResponseEntry.AIContentEntry> updated = change.apply(current);
            if (updated == null) return false;
            fillMissingMessageIds(updated);
            String value = json.toJson(updated), revision = UUID.randomUUID().toString();
            if (row == null) {
                try {
                    db.withSession(
                            c ->
                                    db.repository(c, WebSite.class)
                                            .insert(WebSiteRepository.entry(key, value, revision)));
                    return true;
                } catch (SQLException e) {
                    if (db.withSession(
                                    c -> db.scalar(c, "select name from website where name=?", key))
                            == null) throw e;
                }
            } else {
                Object previous = row.getRemark();
                int count =
                        db.withSession(
                                c ->
                                        db.repository(c, WebSite.class)
                                                .updateWhere(
                                                        WebSiteRepository.entry(
                                                                null, value, revision),
                                                        List.of("value", "remark"),
                                                        "name=? AND "
                                                                + (previous == null
                                                                        ? "remark IS NULL"
                                                                        : "remark=?"),
                                                        previous == null
                                                                ? new Object[] {key}
                                                                : new Object[] {key, previous}));
                if (count == 1) return true;
            }
        }
        throw new SQLException("Conversation changed concurrently");
    }

    static AIResponseEntry.AIContentEntry.TaskFeedbackState feedbackState(
            List<AIResponseEntry.AIContentEntry> messages) {
        return messages.stream()
                .map(AIResponseEntry.AIContentEntry::getTaskFeedbackState)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static AIResponseEntry.AIContentEntry feedbackMarker() {
        var marker = new AIResponseEntry.AIContentEntry("system", "");
        marker.setMessageId("task-feedback-state");
        marker.setMessageType("taskFeedbackState");
        marker.setTaskFeedbackState(new AIResponseEntry.AIContentEntry.TaskFeedbackState());
        return marker;
    }

    /**
     * Materialize authorized feedback in the existing session. CAS merges concurrent chat writes;
     * execution records remain the durable source if synchronization fails and is retried.
     */
    public void synchronizeTaskFeedback(
            com.zrlog.admin.business.automation.AssistantTaskContextProvider.FeedbackPage page,
            Long requestedBefore,
            String requestedBeforeId)
            throws SQLException {
        if (taskId == null) throw new IllegalStateException("Task context required");
        mutateMessages(
                null,
                messages -> {
                    var state = feedbackState(messages);
                    boolean changed = false;
                    if (state == null) {
                        var marker = feedbackMarker();
                        state = marker.getTaskFeedbackState();
                        messages.add(0, marker);
                        changed = true;
                    }
                    for (var run : page.items) {
                        long occurred =
                                run.updatedAt == null
                                        ? Objects.requireNonNullElse(run.finishedAt, 0L)
                                        : run.updatedAt;
                        if (occurred <= state.clearedAt) continue;
                        var entry = TaskResultMessages.entry(run);
                        int index = -1;
                        for (int i = 0; i < messages.size(); i++)
                            if (entry.getMessageId().equals(messages.get(i).getMessageId())) {
                                index = i;
                                break;
                            }
                        if (index < 0) {
                            messages.add(entry);
                            changed = true;
                        } else {
                            var existing = messages.get(index).getTaskResult();
                            if (existing != null && existing.version < run.version) {
                                messages.set(index, entry);
                                changed = true;
                            }
                        }
                    }
                    // A fresh-page refresh must not move the older-message cursor backwards.
                    if (state.before == null
                            || (Objects.equals(state.before, requestedBefore)
                                    && Objects.equals(state.beforeId, requestedBeforeId))) {
                        boolean more =
                                page.hasMore
                                        && page.before != null
                                        && page.before > state.clearedAt;
                        if (!Objects.equals(state.before, page.before)
                                || !Objects.equals(state.beforeId, page.beforeId)
                                || state.hasMore != more) {
                            state.before = page.before;
                            state.beforeId = page.beforeId;
                            state.hasMore = more;
                            changed = true;
                        }
                    }
                    if (!changed) return null;
                    messages.sort(
                            Comparator.comparingLong(
                                    message ->
                                            Objects.requireNonNullElse(
                                                    message.getCreatedAt(), 0L)));
                    return messages;
                });
    }

    public boolean saveAIMessage(List<AIResponseEntry.AIContentEntry> messages, Long articleId)
            throws SQLException {
        return mutateMessages(articleId, ignored -> new ArrayList<>(messages));
    }

    public boolean appendAIMessageEntries(
            List<AIResponseEntry.AIContentEntry> entries, Long articleId) throws SQLException {
        fillMissingMessageIds(entries);
        String prompt = getAiMessageInfoByArticleId(articleId).getAi_prompt();
        return mutateMessages(
                articleId,
                messages -> {
                    ensureSystemMessage(messages, prompt);
                    fillMissingMessageIds(messages);
                    Set<String> ids = new HashSet<>();
                    for (AIResponseEntry.AIContentEntry entry : messages)
                        ids.add(entry.getMessageId());
                    for (AIResponseEntry.AIContentEntry entry : entries)
                        if (ids.add(entry.getMessageId())) messages.add(entry);
                    return messages;
                });
    }

    public boolean updateAIMessagePayload(
            Long articleId, String messageId, String tool, Object payload) throws SQLException {
        return mutateMessages(
                articleId,
                messages -> {
                    for (AIResponseEntry.AIContentEntry entry : messages)
                        if (Objects.equals(messageId, entry.getMessageId())) {
                            entry.setTool(tool);
                            entry.setPayload(payload);
                            return messages;
                        }
                    return null;
                });
    }

    public void ensureSystemMessage(
            List<AIResponseEntry.AIContentEntry> messages, String aiPrompt) {
        boolean hasSystemMessage =
                messages.stream()
                        .anyMatch(
                                message ->
                                        Objects.equals(message.getRole(), "system")
                                                && message.getTaskFeedbackState() == null);
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
