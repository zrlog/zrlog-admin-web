package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

public class MessageCenterService {

    private final UpgradeNoticeService upgradeNoticeService = new UpgradeNoticeService();
    private final AdminCommentService adminCommentService = new AdminCommentService();
    private static final String COMMENT_NOTICE_TYPE = "unreadComment";
    private static final String COMMENT_NOTICE_STATUS = "notice";

    public List<MessageCenterNoticeResponse> listNotices() {
        List<MessageCenterNoticeResponse> notices = new ArrayList<>();
        MessageCenterNoticeResponse versionNotice = upgradeNoticeService.getMessageCenterNotice();
        if (versionNotice != null) {
            notices.add(versionNotice);
        }
        MessageCenterNoticeResponse commentNotice = getUnreadCommentNotice();
        if (commentNotice != null) {
            notices.add(commentNotice);
        }
        return notices;
    }

    private MessageCenterNoticeResponse getUnreadCommentNotice() {
        try {
            int unreadCount = adminCommentService.countUnread();
            if (unreadCount <= 0) {
                return null;
            }
            MessageCenterNoticeResponse response = new MessageCenterNoticeResponse();
            response.setTaskKey("server.comment.unread");
            response.setType(COMMENT_NOTICE_TYPE);
            response.setStatus(COMMENT_NOTICE_STATUS);
            response.setCount(unreadCount);
            response.setUpdatedAt(System.currentTimeMillis());
            return response;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
