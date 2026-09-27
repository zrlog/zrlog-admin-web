package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MessageCenterServiceDatabaseTest {

    @Test
    public void shouldListUnreadCommentNoticeAndSyncMessageCenterState() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedUnreadComment(db);

            List<MessageCenterNoticeResponse> notices = new MessageCenterService().listNotices();
            Map<String, Object> statusRow = db.queryOne("select value from website where name=?",
                    "admin_cache:message_center_status");
            MessageCenterStatusResponse status = new Gson().fromJson(statusRow.get("value").toString(),
                    MessageCenterStatusResponse.class);

            assertEquals(1, notices.size());
            MessageCenterNoticeResponse notice = notices.get(0);
            assertEquals("server.comment.unread", notice.getTaskKey());
            assertEquals("unreadComment", notice.getType());
            assertEquals("notice", notice.getStatus());
            assertNotNull(notice.getUpdatedAt());
            MessageCenterNoticeResponse.UnreadCommentPayload payload =
                    (MessageCenterNoticeResponse.UnreadCommentPayload) notice.getPayload();
            assertEquals(Integer.valueOf(1), payload.getCount());
            assertTrue(status.isHasUnread());
        }
    }

    private static void seedUnreadComment(InMemoryZrLogDatabase db) throws Exception {
        db.execute("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1, "2026-06-29 09:00:00", false, "Unread", "reader@example.com", "",
                "127.0.0.1", "Reader", false, 1, "/reader.png");
    }
}
