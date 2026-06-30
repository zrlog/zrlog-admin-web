package com.zrlog.admin.business.service;

import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.request.WebhookConfigRequest;
import com.zrlog.admin.business.rest.request.WebhookMessageNoticeRequest;
import com.zrlog.admin.business.rest.response.*;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class WebhookService {

    public static final String MESSAGE_CENTER_NOTICE_ENDPOINT = "/api/webhook/message-center/notice";
    public static final String TOKEN_HEADER = "X-ZrLog-Webhook-Token";
    private static final String CONFIG_KEY = "webhook_config";
    private static final String MESSAGE_CENTER_NOTICE_KEY = "webhook_message_center_notices";
    private static final String WEBHOOK_MESSAGE_TYPE = "webhookMessage";
    private static final String NOTICE_STATUS = "notice";
    private static final int MAX_NOTICE_COUNT = 50;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Type NOTICE_LIST_TYPE = new TypeToken<List<WebhookMessageNoticeEntry>>() {
    }.getType();
    private final WebsiteCacheService cacheService = new WebsiteCacheService();
    private final MessageCenterStateService messageCenterStateService = new MessageCenterStateService();

    public WebhookConfigResponse getConfigResponse() {
        return toConfigResponse(getConfig());
    }

    public WebhookConfigResponse updateConfig(WebhookConfigRequest request) {
        WebhookConfigEntry config = getConfig();
        config.setEnabled(Objects.equals(Boolean.TRUE, request.getEnabled()));
        saveConfig(config);
        return toConfigResponse(config);
    }

    public WebhookTokenResponse rotateToken() {
        String token = generateToken();
        WebhookConfigEntry config = getConfig();
        config.setTokenHash(sha256(token));
        config.setTokenPreview(previewToken(token));
        config.setTokenUpdatedAt(System.currentTimeMillis());
        config.setEnabled(true);
        saveConfig(config);

        WebhookTokenResponse response = new WebhookTokenResponse();
        response.setToken(token);
        response.setConfig(toConfigResponse(config));
        return response;
    }

    public WebhookConfigResponse revokeToken() {
        WebhookConfigEntry config = getConfig();
        config.setTokenHash(null);
        config.setTokenPreview(null);
        config.setTokenUpdatedAt(null);
        config.setEnabled(false);
        saveConfig(config);
        return toConfigResponse(config);
    }

    public boolean verifyToken(String token) {
        WebhookConfigEntry config = getConfig();
        if (!Objects.equals(Boolean.TRUE, config.getEnabled()) || StringUtils.isEmpty(config.getTokenHash())
                || StringUtils.isEmpty(token)) {
            return false;
        }
        return MessageDigest.isEqual(config.getTokenHash().getBytes(StandardCharsets.UTF_8),
                sha256(token).getBytes(StandardCharsets.UTF_8));
    }

    public WebhookMessageNoticeCreateResponse createMessageCenterNotice(WebhookMessageNoticeRequest request) {
        long now = System.currentTimeMillis();
        WebhookMessageNoticeEntry notice = new WebhookMessageNoticeEntry();
        notice.setTaskKey(normalizeTaskKey(request.getTaskKey()));
        notice.setTitle(request.getTitle());
        notice.setDescription(request.getDescription());
        notice.setActionLabel(request.getActionLabel());
        notice.setActionPath(request.getActionPath());
        notice.setSource(request.getSource());
        notice.setStatus(NOTICE_STATUS);
        notice.setClosable(!Objects.equals(Boolean.FALSE, request.getClosable()));
        notice.setCreatedAt(now);
        notice.setUpdatedAt(request.getUpdatedAt() != null && request.getUpdatedAt() > 0 ? request.getUpdatedAt() : now);
        notice.setPayload(request.getPayload());

        List<WebhookMessageNoticeEntry> notices = getStoredNotices();
        notices.removeIf(item -> Objects.equals(item.getTaskKey(), notice.getTaskKey()));
        notices.add(0, notice);
        saveNotices(trimNotices(notices));
        messageCenterStateService.markMayHaveUnread();

        WebhookMessageNoticeCreateResponse response = new WebhookMessageNoticeCreateResponse();
        response.setTaskKey(notice.getTaskKey());
        response.setUpdatedAt(notice.getUpdatedAt());
        return response;
    }

    public List<MessageCenterNoticeResponse> listMessageCenterNotices() {
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        for (WebhookMessageNoticeEntry notice : getStoredNotices()) {
            if (notice.getReadAt() != null) {
                continue;
            }
            notices.add(MessageCenterNoticeResponse.of(
                    notice.getTaskKey(),
                    WEBHOOK_MESSAGE_TYPE,
                    NOTICE_STATUS,
                    notice.getUpdatedAt(),
                    MessageCenterNoticeResponse.webhookMessagePayload(notice)
            ));
        }
        return notices;
    }

    public boolean markMessageCenterNoticeRead(String taskKey) {
        List<WebhookMessageNoticeEntry> notices = getStoredNotices();
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (WebhookMessageNoticeEntry notice : notices) {
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

    private WebhookConfigEntry getConfig() {
        WebhookConfigEntry config = cacheService.getJson(CONFIG_KEY, WebhookConfigEntry.class);
        if (config == null) {
            config = new WebhookConfigEntry();
            config.setEnabled(false);
        }
        config.setEnabled(Objects.equals(Boolean.TRUE, config.getEnabled()));
        return config;
    }

    private void saveConfig(WebhookConfigEntry config) {
        cacheService.putJson(CONFIG_KEY, config);
    }

    WebhookConfigResponse toConfigResponse(WebhookConfigEntry config) {
        WebhookConfigResponse response = new WebhookConfigResponse();
        response.setEnabled(Objects.equals(Boolean.TRUE, config.getEnabled()));
        response.setHasToken(StringUtils.isNotEmpty(config.getTokenHash()));
        response.setTokenPreview(config.getTokenPreview());
        response.setTokenUpdatedAt(config.getTokenUpdatedAt());
        response.setEndpoint(MESSAGE_CENTER_NOTICE_ENDPOINT);
        response.setTokenHeader(TOKEN_HEADER);
        return response;
    }

    private List<WebhookMessageNoticeEntry> getStoredNotices() {
        List<WebhookMessageNoticeEntry> notices = cacheService.getJson(MESSAGE_CENTER_NOTICE_KEY, NOTICE_LIST_TYPE);
        return notices == null ? new ArrayList<>() : new ArrayList<>(notices);
    }

    private void saveNotices(List<WebhookMessageNoticeEntry> notices) {
        cacheService.putJson(MESSAGE_CENTER_NOTICE_KEY, notices);
    }

    List<WebhookMessageNoticeEntry> trimNotices(List<WebhookMessageNoticeEntry> notices) {
        notices.sort(Comparator.comparing(WebhookMessageNoticeEntry::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (notices.size() <= MAX_NOTICE_COUNT) {
            return notices;
        }
        return new ArrayList<>(notices.subList(0, MAX_NOTICE_COUNT));
    }

    String normalizeTaskKey(String taskKey) {
        String key = StringUtils.isEmpty(taskKey) ? UUID.randomUUID().toString() : taskKey.trim();
        if (key.startsWith("server.")) {
            return key;
        }
        return "server.webhook.message." + key;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String previewToken(String token) {
        if (token.length() <= 8) {
            return token;
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
