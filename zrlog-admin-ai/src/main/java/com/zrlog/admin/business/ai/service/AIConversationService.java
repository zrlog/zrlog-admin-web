package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.model.WebSite;

import java.sql.SQLException;
import java.util.*;

/** Account-scoped conversation storage, including legacy history migration. */
public class AIConversationService {

    private final Integer conversationUserId;

    public AIConversationService() { this.conversationUserId = null; }

    private AIConversationService(Integer userId) { this.conversationUserId = userId; }

    /** Capture on the request thread before dispatching asynchronous AI work. */
    public AIConversationService captureAccount() { return new AIConversationService(conversationUserId()); }

    private Integer conversationUserId() {
        if (conversationUserId != null) return conversationUserId;
        com.zrlog.common.vo.AdminTokenVO token = com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser();
        return token == null ? null : token.getUserId();
    }

    private String conversationKey(Long articleId) {
        Integer userId = conversationUserId();
        if (userId == null) throw new com.zrlog.admin.business.exception.PermissionErrorException();
        return "ai_chat_message_u" + userId + "_" + articleId;
    }

    private static final long DRAFT_ARTICLE_ID = 0L;
    private static final Object[] AI_MESSAGE_LOCKS = new Object[64];

    static {
        Arrays.setAll(AI_MESSAGE_LOCKS, ignored -> new Object());
    }

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
        int draftLockIndex = aiMessageLockIndex(draftId);
        int articleLockIndex = aiMessageLockIndex(articleId);
        Object draftLock = AI_MESSAGE_LOCKS[draftLockIndex];
        Object articleLock = AI_MESSAGE_LOCKS[articleLockIndex];
        if (draftLock == articleLock) {
            synchronized (draftLock) {
                return migrateDraftAIMessageToArticleUnlocked(articleId, draftId);
            }
        }
        Object firstLock = draftLockIndex < articleLockIndex ? draftLock : articleLock;
        Object secondLock = draftLockIndex < articleLockIndex ? articleLock : draftLock;
        synchronized (firstLock) {
            synchronized (secondLock) {
                return migrateDraftAIMessageToArticleUnlocked(articleId, draftId);
            }
        }
    }

    private boolean migrateDraftAIMessageToArticleUnlocked(Long articleId, Long draftId) throws SQLException {
        WebsiteKvService kvService = new WebsiteKvService();
        String draftAIMessageKey = conversationKey(draftId);
        Map<String, Object> draftValues = new WebSite().getWebSiteByNameIn(
                Arrays.asList(draftAIMessageKey, buildCacheKey(draftId)));
        String draftAIMessage = (String) draftValues.get(draftAIMessageKey);
        if (draftAIMessage == null) draftAIMessage = (String) draftValues.get(buildCacheKey(draftId));
        if (StringUtils.isEmpty(draftAIMessage)) {
            return false;
        }
        boolean saved = kvService.putString(conversationKey(articleId), draftAIMessage);
        if (saved) {
            kvService.putString(draftAIMessageKey, "[]");
        }
        return saved;
    }

    public boolean removeAIMessage(Long articleId) {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        try {
            // Article deletion is already authorized by AdminArticleService.
            new WebSite().execute("update website set value=null where name like ? escape '!'", "ai!_chat!_message!_u%!_" + articleId);
            return new WebsiteKvService().remove(buildCacheKey(articleId));
        } catch (SQLException e) { throw new IllegalStateException("Unable to remove article AI messages", e); }
    }

    public boolean clearAIMessage(Long articleId) {
        if (articleId == null || (articleId < DRAFT_ARTICLE_ID && articleId != -(long) Objects.requireNonNullElse(conversationUserId(), 0))) {
            return false;
        }
        synchronized (aiMessageLock(articleId)) {
            return new WebsiteKvService().putStringQuietly(conversationKey(articleId), "[]");
        }
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
        Map<String, Object> map = AIConfigService.queryToMap(names, Map.class);
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

    private static Object aiMessageLock(Long articleId) {
        return AI_MESSAGE_LOCKS[aiMessageLockIndex(articleId)];
    }

    private static int aiMessageLockIndex(Long articleId) {
        return Math.floorMod(Objects.hashCode(articleId), AI_MESSAGE_LOCKS.length);
    }

    private boolean saveAIMessageUnlocked(List<AIResponseEntry.AIContentEntry> messages, Long articleId)
            throws SQLException {
        fillMissingMessageIds(messages);
        String jsonStr = new Gson().toJson(messages);
        return new WebsiteKvService().putString(conversationKey(articleId), jsonStr);
    }

    public boolean saveAIMessage(List<AIResponseEntry.AIContentEntry> messages, Long articleId) throws SQLException {
        synchronized (aiMessageLock(articleId)) {
            return saveAIMessageUnlocked(messages, articleId);
        }
    }

    public boolean appendAIMessageEntries(List<AIResponseEntry.AIContentEntry> entries, Long articleId)
            throws SQLException {
        synchronized (aiMessageLock(articleId)) {
            AIWebSiteInfoWithAIMessages currentInfo = getAiMessageInfoByArticleId(articleId);
            List<AIResponseEntry.AIContentEntry> currentMessages = currentInfo.getAiMessages();
            ensureSystemMessage(currentMessages, currentInfo.getAi_prompt());
            fillMissingMessageIds(currentMessages);
            fillMissingMessageIds(entries);
            Set<String> currentIds = new HashSet<>();
            for (AIResponseEntry.AIContentEntry currentMessage : currentMessages) {
                currentIds.add(currentMessage.getMessageId());
            }
            for (AIResponseEntry.AIContentEntry entry : entries) {
                if (currentIds.add(entry.getMessageId())) {
                    currentMessages.add(entry);
                }
            }
            return saveAIMessageUnlocked(currentMessages, articleId);
        }
    }

    public boolean updateAIMessagePayload(Long articleId, String messageId, String tool, Object payload)
            throws SQLException {
        synchronized (aiMessageLock(articleId)) {
            AIWebSiteInfoWithAIMessages info = getAiMessageInfoByArticleId(articleId);
            List<AIResponseEntry.AIContentEntry> messages = info.getAiMessages();
            boolean changed = false;
            for (AIResponseEntry.AIContentEntry message : messages) {
                if (Objects.equals(messageId, message.getMessageId())) {
                    message.setTool(tool);
                    message.setPayload(payload);
                    changed = true;
                    break;
                }
            }
            return changed && saveAIMessageUnlocked(messages, articleId);
        }
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
