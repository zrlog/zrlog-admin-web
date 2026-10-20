package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.business.dto.StoredUpgradeNotice;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.service.UpgradeNoticeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MessageCenterService {

    private static final String VERSION_UPDATE_NOTICE_TYPE = "versionUpdate";
    private static final String COMMENT_NOTICE_TYPE = "unreadComment";
    private static final String COMMENT_NOTICE_STATUS = "notice";
    private final UpgradeNoticeService upgradeNoticeService;
    private final AdminCommentService adminCommentService;
    private final WebhookService webhookService;
    private final MessageCenterOperationService operationService;
    private final MessageCenterStateService messageCenterStateService;
    @Inject
    public MessageCenterService(
            UpgradeNoticeService upgradeNoticeService,
            WebhookService webhookService,
            MessageCenterStateService messageCenterStateService,
            MessageCenterOperationService operationService,
            AdminCommentService adminCommentService) {
        this.webhookService = webhookService;
        this.messageCenterStateService = messageCenterStateService;
        this.operationService = operationService;
        this.adminCommentService = adminCommentService;

        this.upgradeNoticeService = upgradeNoticeService;
    }

    public List<MessageCenterNoticeResponse> listNotices() throws SQLException {
        long observedRevision = messageCenterStateService.current().getRevision();
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        MessageCenterNoticeResponse versionNotice = getVersionUpdateNotice();
        if (versionNotice != null) {
            notices.add(versionNotice);
        }
        MessageCenterNoticeResponse commentNotice = getUnreadCommentNotice();
        if (commentNotice != null) {
            notices.add(commentNotice);
        }
        notices.addAll(operationService.listOperationNotices());
        notices.addAll(webhookService.listMessageCenterNotices());
        messageCenterStateService.syncActual(observedRevision, !notices.isEmpty());
        return notices;
    }

    public boolean markRead(String taskKey) {
        return webhookService.markMessageCenterNoticeRead(taskKey)
                || operationService.markOperationNoticeRead(taskKey);
    }

    private MessageCenterNoticeResponse getVersionUpdateNotice() {
        StoredUpgradeNotice storedUpgradeNotice = upgradeNoticeService.getStoredUpgradeNotice();
        if (storedUpgradeNotice == null || storedUpgradeNotice.getVersion() == null) {
            return null;
        }
        CheckVersionResponse checkVersionResponse = upgradeNoticeService.buildResponse(storedUpgradeNotice.getVersion());
        if (!Boolean.TRUE.equals(checkVersionResponse.getUpgrade()) || checkVersionResponse.getVersion() == null) {
            upgradeNoticeService.clearStoredUpgradeNotice();
            return null;
        }
        return MessageCenterNoticeResponse.of(
                "server.system.version-update",
                VERSION_UPDATE_NOTICE_TYPE,
                COMMENT_NOTICE_STATUS,
                storedUpgradeNotice.getUpdatedAt(),
                MessageCenterNoticeResponse.versionUpdatePayload(checkVersionResponse.getVersion())
        );
    }

    private MessageCenterNoticeResponse getUnreadCommentNotice() throws SQLException {
        int unreadCount = adminCommentService.countUnread();
        if (unreadCount <= 0) {
            return null;
        }
        return MessageCenterNoticeResponse.of(
                "server.comment.unread",
                COMMENT_NOTICE_TYPE,
                COMMENT_NOTICE_STATUS,
                System.currentTimeMillis(),
                MessageCenterNoticeResponse.unreadCommentPayload(unreadCount)
        );

    }
}
