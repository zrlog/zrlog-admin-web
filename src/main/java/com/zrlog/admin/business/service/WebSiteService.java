package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.AddArticleAIContextRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleAIMessageExportResponse;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.WebSite;

import java.sql.SQLException;
import java.util.*;

public class WebSiteService {

    public static final String ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY = "article_editor_link_preview_enabled";
    public static final String ARTICLE_PUBLISH_CHECK_ENABLED_KEY = "article_publish_check_enabled";
    public static final String ARTICLE_COVER_ASPECT_RATIO_KEY = "article_cover_aspect_ratio";
    public static final String ARTICLE_EDIT_AUTO_SAVE_INTERVAL_KEY = "article_edit_auto_save_interval";
    public static final String FEATURE_RESOURCE_REFERENCE_ENABLED_KEY = "feature_resource_reference_enabled";
    public static final String FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY =
            "feature_article_extension_filter_enabled";
    public static final String FEATURE_WEBHOOK_ENABLED_KEY = "feature_webhook_enabled";
    public static final String FEATURE_PERSONAL_DATA_ENABLED_KEY = "feature_personal_data_enabled";
    public static final String AI_REASONING_ENABLED_KEY = "ai_reasoning_enabled";
    private static final List<String> AI_WEBSITE_INFO_KEYS = Arrays.asList("ai_provider", "ai_model", "ai_base_url", "ai_api_key", "ai_prompt",
            "ai_max_completion_tokens", AI_REASONING_ENABLED_KEY, "ai_image_provider", "ai_image_model",
            "ai_image_base_url", "ai_image_api_key");
    private static final List<String> ARTICLE_EDIT_WEBSITE_INFO_KEYS = Arrays.asList(WebSite.article_auto_digest_length,
            ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY, ARTICLE_PUBLISH_CHECK_ENABLED_KEY,
            ARTICLE_COVER_ASPECT_RATIO_KEY, ARTICLE_EDIT_AUTO_SAVE_INTERVAL_KEY);
    private static final long DRAFT_ARTICLE_ID = 0L;
    private static final Object[] AI_MESSAGE_LOCKS = new Object[64];

    static {
        Arrays.setAll(AI_MESSAGE_LOCKS, ignored -> new Object());
    }

    public UpgradeWebSiteInfo upgradeWebSiteInfo() {
        return new WebsiteKvService().upgradeWebSiteInfo();
    }

    public FeatureLabWebSiteInfo featureLab() {
        FeatureLabWebSiteInfo featureLab = queryToMap(Arrays.asList(FEATURE_RESOURCE_REFERENCE_ENABLED_KEY,
                FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY,
                FEATURE_WEBHOOK_ENABLED_KEY, FEATURE_PERSONAL_DATA_ENABLED_KEY), FeatureLabWebSiteInfo.class);
        featureLab.doValid();
        return featureLab;
    }

    public boolean isFeatureResourceReferenceEnabled() {
        return Objects.equals(true, featureLab().getFeature_resource_reference_enabled());
    }

    public boolean isFeatureArticleExtensionFilterEnabled() {
        return Objects.equals(true, featureLab().getFeature_article_extension_filter_enabled());
    }

    public BlogWebSiteInfo blogWebSiteInfo() {
        BlogWebSiteInfo blog = queryToMap(Arrays.asList(WebSite.generator_html_status, WebSite.host, WebSite.disable_comment_status, WebSite.article_thumbnail_status, WebSite.system_notification), BlogWebSiteInfo.class);
        blog.setGenerator_html_status(Objects.equals(blog.getGenerator_html_status(), true));
        blog.setDisable_comment_status(Objects.equals(blog.getDisable_comment_status(), true));
        blog.setArticle_thumbnail_status(Objects.equals(blog.getArticle_thumbnail_status(), true));
        return blog;
    }

    public BasicWebSiteInfo basicWebSiteInfo() {
        return queryToMap(Arrays.asList(WebSite.title, WebSite.second_title, WebSite.description, WebSite.keywords, "favicon_ico_base64", WebSite.author), BasicWebSiteInfo.class);
    }

    public AdminWebSiteInfo adminWebSiteInfo() {
        AdminWebSiteInfo admin = queryToMap(Arrays.asList(WebSite.admin_darkMode, WebSite.admin_compactMode,
                WebSite.language, WebSite.admin_color_primary, WebSite.admin_theme,
                WebSite.session_timeout, "favicon_png_pwa_512_base64",
                "favicon_png_pwa_192_base64", "admin_article_page_size", "admin_static_resource_base_url"),
                AdminWebSiteInfo.class);
        if (StringUtils.isEmpty(admin.getAdmin_color_primary())) {
            admin.setAdmin_color_primary(WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR);
        }
        if (Objects.isNull(admin.getAdmin_article_page_size()) || admin.getAdmin_article_page_size() <= 0) {
            admin.setAdmin_article_page_size(10L);
        }
        if (Objects.isNull(admin.getSession_timeout()) || admin.getSession_timeout() <= 0) {
            admin.setSession_timeout(WebSiteUtils.DEFAULT_SESSION_TIMEOUT / 60 / 1000);
        }
        return admin;
    }

    public ArticleEditWebSiteInfo articleEditWebSiteInfo() {
        ArticleEditWebSiteInfo articleEdit = queryToMap(ARTICLE_EDIT_WEBSITE_INFO_KEYS, ArticleEditWebSiteInfo.class);
        return normalizeArticleEditWebSiteInfo(articleEdit);
    }

    ArticleEditWebSiteInfo normalizeArticleEditWebSiteInfo(ArticleEditWebSiteInfo articleEdit) {
        if (Objects.isNull(articleEdit.getArticle_auto_digest_length()) || articleEdit.getArticle_auto_digest_length() <= 0) {
            articleEdit.setArticle_auto_digest_length(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH);
        }
        articleEdit.setArticle_edit_auto_save_interval(ArticleEditWebSiteInfo.normalizeArticleEditAutoSaveInterval(articleEdit.getArticle_edit_auto_save_interval()));
        articleEdit.setArticle_editor_link_preview_enabled(Objects.equals(articleEdit.getArticle_editor_link_preview_enabled(), true));
        articleEdit.setArticle_publish_check_enabled(!Objects.equals(articleEdit.getArticle_publish_check_enabled(), false));
        articleEdit.setArticle_cover_aspect_ratio(ArticleEditWebSiteInfo.normalizeArticleCoverAspectRatio(articleEdit.getArticle_cover_aspect_ratio()));
        return articleEdit;
    }

    public ContentProtectorWebSiteInfo contentProtector() {
        ContentProtectorWebSiteInfo contentProtector = queryToMap(Arrays.asList(WebSite.content_protector_enabled,
                WebSite.content_protector_license_type, WebSite.content_protector_template),
                ContentProtectorWebSiteInfo.class);
        contentProtector.doValid();
        return contentProtector;
    }

    public boolean isAdminLinkPreviewEnabled() {
        return Objects.equals(Boolean.TRUE, queryToMap(Arrays.asList(ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY),
                ArticleEditWebSiteInfo.class).getArticle_editor_link_preview_enabled());
    }

    public AIWebSiteInfo ai() {
        return normalizeAIWebSiteInfo(queryToMap(AI_WEBSITE_INFO_KEYS, AIWebSiteInfo.class));
    }

    <T extends AIWebSiteInfo> T normalizeAIWebSiteInfo(T info) {
        if (info.getAi_reasoning_enabled() == null) {
            info.setAi_reasoning_enabled(Boolean.TRUE);
        }
        return info;
    }


    static String buildCacheKey(Long articleId) {
        return "ai_chat_message_" + articleId;
    }

    public boolean migrateDraftAIMessageToArticle(Long articleId) throws SQLException {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        int draftLockIndex = aiMessageLockIndex(DRAFT_ARTICLE_ID);
        int articleLockIndex = aiMessageLockIndex(articleId);
        Object draftLock = AI_MESSAGE_LOCKS[draftLockIndex];
        Object articleLock = AI_MESSAGE_LOCKS[articleLockIndex];
        if (draftLock == articleLock) {
            synchronized (draftLock) {
                return migrateDraftAIMessageToArticleUnlocked(articleId);
            }
        }
        Object firstLock = draftLockIndex < articleLockIndex ? draftLock : articleLock;
        Object secondLock = draftLockIndex < articleLockIndex ? articleLock : draftLock;
        synchronized (firstLock) {
            synchronized (secondLock) {
                return migrateDraftAIMessageToArticleUnlocked(articleId);
            }
        }
    }

    private boolean migrateDraftAIMessageToArticleUnlocked(Long articleId) throws SQLException {
        WebsiteKvService kvService = new WebsiteKvService();
        String draftAIMessageKey = buildCacheKey(DRAFT_ARTICLE_ID);
        String draftAIMessage = kvService.getString(draftAIMessageKey);
        if (StringUtils.isEmpty(draftAIMessage)) {
            return false;
        }
        boolean saved = kvService.putString(buildCacheKey(articleId), draftAIMessage);
        if (saved) {
            kvService.removeQuietly(draftAIMessageKey);
        }
        return saved;
    }

    public boolean removeAIMessage(Long articleId) {
        if (articleId == null || articleId <= DRAFT_ARTICLE_ID) {
            return false;
        }
        return clearAIMessage(articleId);
    }

    public boolean clearAIMessage(Long articleId) {
        if (articleId == null || articleId < DRAFT_ARTICLE_ID) {
            return false;
        }
        synchronized (aiMessageLock(articleId)) {
            return new WebsiteKvService().removeQuietly(buildCacheKey(articleId));
        }
    }

    public ArticleAIMessageExportResponse exportAIMessage(Long articleId) {
        AIWebSiteInfoWithAIMessages info = getAiMessageInfoByArticleId(articleId);
        ArticleAIMessageExportResponse response = new ArticleAIMessageExportResponse();
        response.setArticleId(articleId);
        response.setDraft(Objects.equals(articleId, DRAFT_ARTICLE_ID));
        response.setExportedAt(System.currentTimeMillis());
        response.setMessages(info.getAiMessages());
        response.setMessageCount(info.getAiMessages().size());
        return response;
    }

    public AIWebSiteInfoWithAIMessages getAiMessageInfoByArticleId(Long articleId) {
        String aiMessageKey = buildCacheKey(articleId);
        List<String> names = new ArrayList<>(AI_WEBSITE_INFO_KEYS);
        names.add(aiMessageKey);
        Map<String, Object> map = queryToMap(names, Map.class);
        AIWebSiteInfoWithAIMessages info = normalizeAIWebSiteInfo(
                ResultBeanUtils.convert(map, AIWebSiteInfoWithAIMessages.class));
        fillAiMessages(info, map, aiMessageKey);
        return info;
    }

    public ArticleEditorContext articleEditorContext(Long articleId) {
        String aiMessageKey = buildCacheKey(articleId);
        List<String> names = new ArrayList<>(AI_WEBSITE_INFO_KEYS.size() + ARTICLE_EDIT_WEBSITE_INFO_KEYS.size() + 1);
        names.addAll(AI_WEBSITE_INFO_KEYS);
        names.addAll(ARTICLE_EDIT_WEBSITE_INFO_KEYS);
        names.add(aiMessageKey);
        Map<String, Object> map = queryToMap(names, Map.class);
        AIWebSiteInfoWithAIMessages ai = normalizeAIWebSiteInfo(
                ResultBeanUtils.convert(map, AIWebSiteInfoWithAIMessages.class));
        fillAiMessages(ai, map, aiMessageKey);
        ArticleEditWebSiteInfo articleEdit = normalizeArticleEditWebSiteInfo(ResultBeanUtils.convert(map, ArticleEditWebSiteInfo.class));
        return new ArticleEditorContext(ai, articleEdit);
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

    public static class ArticleEditorContext {

        private final AIWebSiteInfoWithAIMessages ai;
        private final ArticleEditWebSiteInfo articleEdit;

        private ArticleEditorContext(AIWebSiteInfoWithAIMessages ai, ArticleEditWebSiteInfo articleEdit) {
            this.ai = ai;
            this.articleEdit = articleEdit;
        }

        public AIWebSiteInfoWithAIMessages getAi() {
            return ai;
        }

        public ArticleEditWebSiteInfo getArticleEdit() {
            return articleEdit;
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
        return new WebsiteKvService().putString(buildCacheKey(articleId), jsonStr);
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

    public List<AIResponseEntry.AIContentEntry> appendArticleContextMessage(Long articleId,
                                                                            AddArticleAIContextRequest contextRequest)
            throws SQLException {
        synchronized (aiMessageLock(articleId)) {
            AIWebSiteInfoWithAIMessages info = getAiMessageInfoByArticleId(articleId);
            List<AIResponseEntry.AIContentEntry> messages = info.getAiMessages();
            ensureSystemMessage(messages, info.getAi_prompt());
            AIResponseEntry.AIContentEntry contextMessage =
                    new AIResponseEntry.AIContentEntry("user", buildArticleContextContent(contextRequest));
            contextMessage.setMessageType("articleContext");
            contextMessage.setContextMeta(buildArticleContextMeta(contextRequest));
            messages.add(contextMessage);
            if (!saveAIMessageUnlocked(messages, articleId)) {
                throw new SQLException("save article AI context message failed");
            }
            return messages;
        }
    }

    public void ensureSystemMessage(List<AIResponseEntry.AIContentEntry> messages, String aiPrompt) {
        boolean hasSystemMessage = messages.stream().anyMatch(message -> Objects.equals(message.getRole(), "system"));
        if (!hasSystemMessage) {
            messages.add(0, new AIResponseEntry.AIContentEntry("system", emptyToBlank(aiPrompt)));
        }
    }

    AIResponseEntry.AIContentEntry.ArticleContextMeta buildArticleContextMeta(
            AddArticleAIContextRequest contextRequest) {
        AIResponseEntry.AIContentEntry.ArticleContextMeta meta =
                new AIResponseEntry.AIContentEntry.ArticleContextMeta();
        meta.setTitle(contextRequest.getTitle());
        meta.setArticleVersion(contextRequest.getArticleVersion());
        meta.setMarkdownLength(emptyToBlank(contextRequest.getMarkdown()).length());
        meta.setCreatedAt(System.currentTimeMillis());
        return meta;
    }

    String buildArticleContextContent(AddArticleAIContextRequest contextRequest) {
        StringBuilder sb = new StringBuilder();
        sb.append("Article context snapshot.\n");
        if (contextRequest.getArticleVersion() != null) {
            sb.append("Article version: ").append(contextRequest.getArticleVersion()).append("\n");
        }
        sb.append("Title: ").append(emptyToBlank(contextRequest.getTitle())).append("\n");
        sb.append("Digest: ").append(emptyToBlank(contextRequest.getDigest())).append("\n");
        sb.append("Keywords: ").append(emptyToBlank(contextRequest.getKeywords())).append("\n");
        sb.append("Markdown:\n").append(emptyToBlank(contextRequest.getMarkdown()));
        return sb.toString();
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

    private <T> T queryToMap(List<String> names, Class<T> clazz) {
        Map<String, Object> webSiteByNameIn = new WebSite().getWebSiteByNameIn(names);
        return ResultBeanUtils.convert(webSiteByNameIn, clazz);
    }

    public OtherWebSiteInfo other() {
        return queryToMap(Arrays.asList(WebSite.icp, WebSite.webCm, WebSite.robotRuleContent), OtherWebSiteInfo.class);
    }
}
