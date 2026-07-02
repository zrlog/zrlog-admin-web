package com.zrlog.admin.business.service;

import com.google.gson.reflect.TypeToken;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.MessageCenterOperationNoticeEntry;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.util.I18nUtil;

import java.lang.reflect.Type;
import java.util.*;

public class MessageCenterOperationService {

    private static final String OPERATION_NOTICE_KEY = "message_center_operation_notices";
    private static final String OPERATION_TYPE = "operationTask";
    private static final String REPLACE_ARTICLE_RESOURCE_URL_ACTION = "replaceArticleResourceUrl";
    private static final String PUBLISH_CHECK_ACTION = "publishCheck";
    private static final String STATIC_SITE_SYNC_ACTION = "staticSiteSync";
    private static final String UPGRADE_ACTION = "upgrade";
    private static final String UPGRADE_RESTART_ACTION = "upgradeRestart";
    private static final String BLOG_STATIC_SITE_SYNC_ACTION = "blogStaticSiteSync";
    private static final int PUBLISH_CHECK_SUCCESS_SCORE = 80;
    private static final int MAX_NOTICE_COUNT = 50;
    private static final Set<String> FINISH_STATUSES = Set.of("success", "warning", "error", "cancelled");
    private static final Type NOTICE_LIST_TYPE = new TypeToken<List<MessageCenterOperationNoticeEntry>>() {
    }.getType();
    private final WebsiteCacheService cacheService = new WebsiteCacheService();
    private final MessageCenterStateService messageCenterStateService = new MessageCenterStateService();

    public void recordReplaceArticleResourceUrl(ReplaceArticleResourceUrlResponse response) {
        long now = System.currentTimeMillis();
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(REPLACE_ARTICLE_RESOURCE_URL_ACTION, now));
        notice.setTitle(adminMessage("admin.messageCenter.operation.replaceArticleResourceUrl.title"));
        notice.setDescription(adminMessage("admin.messageCenter.operation.replaceArticleResourceUrl.description")
                .replace("{articles}", String.valueOf(response.getUpdatedArticles()))
                .replace("{fields}", String.valueOf(response.getUpdatedFields()))
                .replace("{scanned}", String.valueOf(response.getScannedArticles())));
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.fileManager.action"));
        notice.setActionPath("/file-manager?resourceType=external");
        notice.setSource(adminMessage("admin.fileManager.title"));
        notice.setStatus(response.getUpdatedArticles() > 0 ? "success" : "warning");
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        notice.setPayload(new MessageCenterOperationNoticeEntry.ReplaceArticleResourceUrlPayload(
                response.getScannedArticles(), response.getUpdatedArticles(), response.getUpdatedFields()));
        saveNotice(notice);
    }

    public void recordStaticSiteSync(List<StaticSiteType> siteTypes, boolean synced) {
        long now = System.currentTimeMillis();
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(STATIC_SITE_SYNC_ACTION, now));
        notice.setTitle(adminMessage(synced
                ? "admin.messageCenter.operation.staticSiteSync.title"
                : "admin.messageCenter.operation.staticSiteSync.warningTitle"));
        notice.setDescription(adminMessage(synced
                ? "admin.messageCenter.operation.staticSiteSync.description"
                : "admin.messageCenter.operation.staticSiteSync.warningDescription"));
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.staticSiteSync.action"));
        notice.setActionPath("/website/upgrade");
        notice.setSource(adminMessage("admin.website.upgrade.manage"));
        notice.setStatus(synced ? "success" : "warning");
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        List<String> siteTypeNames = new ArrayList<>();
        for (StaticSiteType siteType : siteTypes) {
            siteTypeNames.add(siteType.name());
        }
        notice.setPayload(new MessageCenterOperationNoticeEntry.StaticSiteSyncPayload(siteTypeNames, synced));
        saveNotice(notice);
    }

    public void recordBlogStaticSiteSync(boolean synced, String message) {
        long now = System.currentTimeMillis();
        String description = Objects.toString(message, "").trim();
        if (description.isEmpty()) {
            description = adminMessage(synced
                    ? "admin.messageCenter.operation.blogStaticSiteSync.description"
                    : "admin.messageCenter.operation.blogStaticSiteSync.errorDescription");
        }
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(BLOG_STATIC_SITE_SYNC_ACTION, now));
        notice.setTitle(adminMessage(synced
                ? "admin.messageCenter.operation.blogStaticSiteSync.title"
                : "admin.messageCenter.operation.blogStaticSiteSync.errorTitle"));
        notice.setDescription(description);
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.articleManage.action"));
        notice.setActionPath("/article");
        notice.setSource(adminMessage("admin.article.manage"));
        notice.setStatus(synced ? "success" : "error");
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        notice.setPayload(new MessageCenterOperationNoticeEntry.StaticSiteSyncPayload(
                List.of(StaticSiteType.BLOG.name()), synced));
        saveNotice(notice);
    }

    public void recordUpgradeResult(UpgradeProcessResponse response) {
        boolean finish = Boolean.TRUE.equals(response.getFinish());
        String message = Objects.toString(response.getMessage(), "").trim();
        String defaultDescriptionKey = finish
                ? "admin.messageCenter.operation.upgrade.description"
                : "admin.messageCenter.operation.upgrade.warningDescription";
        recordUpgradeNotice(
                finish
                        ? "admin.messageCenter.operation.upgrade.title"
                        : "admin.messageCenter.operation.upgrade.warningTitle",
                message.isEmpty() ? adminMessage(defaultDescriptionKey) : message,
                finish ? "success" : "warning",
                finish,
                message
        );
    }

    public void recordUpgradeError(String message) {
        String description = Objects.toString(message, "").trim();
        if (description.isEmpty()) {
            description = adminMessage("admin.messageCenter.operation.upgrade.errorDescription");
        }
        recordUpgradeNotice(
                "admin.messageCenter.operation.upgrade.errorTitle",
                description,
                "error",
                false,
                description
        );
    }

    public void recordUpgradeRestart(String status, String buildId) {
        String normalizedStatus = normalizeStatus(status);
        if (!Set.of("success", "warning", "error").contains(normalizedStatus)) {
            normalizedStatus = "notice";
        }
        String titleKey = "admin.messageCenter.operation.upgradeRestart.title";
        String descriptionKey = "admin.messageCenter.operation.upgradeRestart.description";
        if (Objects.equals(normalizedStatus, "warning")) {
            titleKey = "admin.messageCenter.operation.upgradeRestart.warningTitle";
            descriptionKey = "admin.messageCenter.operation.upgradeRestart.warningDescription";
        } else if (Objects.equals(normalizedStatus, "error")) {
            titleKey = "admin.messageCenter.operation.upgradeRestart.errorTitle";
            descriptionKey = "admin.messageCenter.operation.upgradeRestart.errorDescription";
        }
        String buildIdText = Objects.toString(buildId, "").trim();
        long now = System.currentTimeMillis();
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(UPGRADE_RESTART_ACTION, now));
        notice.setTitle(adminMessage(titleKey));
        notice.setDescription(adminMessage(descriptionKey)
                .replace("{buildId}", buildIdText.isEmpty() ? "-" : buildIdText));
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.upgrade.action"));
        notice.setActionPath("/upgrade");
        notice.setSource(adminMessage("admin.upgrade.wizard.manage"));
        notice.setStatus(normalizedStatus);
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        notice.setPayload(new MessageCenterOperationNoticeEntry.UpgradeRestartPayload(buildIdText));
        saveNotice(notice);
    }

    public void recordPublishCheckSuccess(Long articleId, String articleTitle, Object checkPayload) {
        long now = System.currentTimeMillis();
        Integer score = extractScore(checkPayload);
        int itemCount = extractItemCount(checkPayload);
        MessageCenterOperationNoticeEntry notice = buildArticleOperationNotice(
                PUBLISH_CHECK_ACTION,
                now,
                "admin.messageCenter.operation.publishCheck.title",
                adminMessage("admin.messageCenter.operation.publishCheck.description")
                        .replace("{score}", score == null ? "-" : String.valueOf(score))
                        .replace("{items}", String.valueOf(itemCount)),
                score != null && score >= PUBLISH_CHECK_SUCCESS_SCORE ? "success" : "warning",
                articleId
        );
        notice.setPayload(new MessageCenterOperationNoticeEntry.PublishCheckPayload(
                articleId, articleTitle, score, itemCount));
        saveNotice(notice);
    }

    public void recordPublishCheckError(Long articleId, String articleTitle, String message) {
        long now = System.currentTimeMillis();
        String description = Objects.toString(message, "").trim();
        if (description.isEmpty()) {
            description = adminMessage("admin.messageCenter.operation.publishCheck.errorDescription");
        }
        MessageCenterOperationNoticeEntry notice = buildArticleOperationNotice(
                PUBLISH_CHECK_ACTION,
                now,
                "admin.messageCenter.operation.publishCheck.errorTitle",
                description,
                "error",
                articleId
        );
        notice.setPayload(new MessageCenterOperationNoticeEntry.PublishCheckPayload(
                articleId, articleTitle, null, null));
        saveNotice(notice);
    }

    public List<MessageCenterNoticeResponse> listOperationNotices() {
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        for (MessageCenterOperationNoticeEntry notice : getStoredNotices()) {
            if (notice.getReadAt() != null) {
                continue;
            }
            notices.add(MessageCenterNoticeResponse.of(
                    notice.getTaskKey(),
                    OPERATION_TYPE,
                    normalizeStatus(notice.getStatus()),
                    notice.getUpdatedAt(),
                    MessageCenterNoticeResponse.operationPayload(notice)
            ));
        }
        return notices;
    }

    public boolean markOperationNoticeRead(String taskKey) {
        List<MessageCenterOperationNoticeEntry> notices = getStoredNotices();
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (MessageCenterOperationNoticeEntry notice : notices) {
            if (Objects.equals(notice.getTaskKey(), taskKey)) {
                notice.setReadAt(now);
                changed = true;
                break;
            }
        }
        if (changed) {
            saveNotices(notices);
            messageCenterStateService.markChanged();
        }
        return changed;
    }

    private void saveNotice(MessageCenterOperationNoticeEntry notice) {
        List<MessageCenterOperationNoticeEntry> notices = getStoredNotices();
        notices.removeIf(item -> Objects.equals(item.getTaskKey(), notice.getTaskKey()));
        notices.add(0, notice);
        saveNotices(trimNotices(notices));
        messageCenterStateService.markMayHaveUnread();
    }

    private List<MessageCenterOperationNoticeEntry> getStoredNotices() {
        List<MessageCenterOperationNoticeEntry> notices = cacheService.getJson(OPERATION_NOTICE_KEY, NOTICE_LIST_TYPE);
        return notices == null ? new ArrayList<>() : new ArrayList<>(notices);
    }

    private void saveNotices(List<MessageCenterOperationNoticeEntry> notices) {
        cacheService.putJson(OPERATION_NOTICE_KEY, notices);
    }

    private List<MessageCenterOperationNoticeEntry> trimNotices(List<MessageCenterOperationNoticeEntry> notices) {
        notices.sort(Comparator.comparing(MessageCenterOperationNoticeEntry::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (notices.size() <= MAX_NOTICE_COUNT) {
            return notices;
        }
        return new ArrayList<>(notices.subList(0, MAX_NOTICE_COUNT));
    }

    private String buildTaskKey(String action, long timestamp) {
        return "server.operation." + action + "." + timestamp + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private void recordUpgradeNotice(String titleKey, String description, String status, boolean finish, String message) {
        long now = System.currentTimeMillis();
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(UPGRADE_ACTION, now));
        notice.setTitle(adminMessage(titleKey));
        notice.setDescription(description);
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.upgrade.action"));
        notice.setActionPath("/upgrade");
        notice.setSource(adminMessage("admin.upgrade.wizard.manage"));
        notice.setStatus(status);
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        notice.setPayload(new MessageCenterOperationNoticeEntry.UpgradePayload(finish, message));
        saveNotice(notice);
    }

    private MessageCenterOperationNoticeEntry buildArticleOperationNotice(String action, long now, String titleKey,
                                                                          String description, String status,
                                                                          Long articleId) {
        MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
        notice.setTaskKey(buildTaskKey(action, now));
        notice.setTitle(adminMessage(titleKey));
        notice.setDescription(description);
        notice.setActionLabel(adminMessage("admin.messageCenter.operation.articleEdit.action"));
        notice.setActionPath("/article-edit?id=" + articleId);
        notice.setSource(adminMessage("admin.article.edit.manage"));
        notice.setStatus(status);
        notice.setClosable(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        return notice;
    }

    private Integer extractScore(Object checkPayload) {
        if (checkPayload instanceof ScoreArticleResponse) {
            return ((ScoreArticleResponse) checkPayload).getScore();
        }
        if (checkPayload instanceof Map) {
            return toInteger(((Map<?, ?>) checkPayload).get("score"));
        }
        return null;
    }

    private int extractItemCount(Object checkPayload) {
        if (checkPayload instanceof ScoreArticleResponse) {
            List<ScoreArticleResponse.ScoreItem> items = ((ScoreArticleResponse) checkPayload).getItems();
            return items == null ? 0 : items.size();
        }
        if (checkPayload instanceof Map) {
            Object items = ((Map<?, ?>) checkPayload).get("items");
            if (items instanceof Collection) {
                return ((Collection<?>) items).size();
            }
        }
        return 0;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeStatus(String status) {
        return status != null && FINISH_STATUSES.contains(status) ? status : "notice";
    }

    private String adminMessage(String key) {
        return I18nUtil.getAdminBackendStringFromRes(key);
    }
}
