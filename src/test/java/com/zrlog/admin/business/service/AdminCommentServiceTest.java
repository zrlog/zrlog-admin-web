package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.request.ReadCommentRequest;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AdminCommentServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldParseCommentIdsWithTrimAndStableDeduplication() throws Exception {
        AdminCommentService service = new AdminCommentService();
        Method method = method("parseCommentIds", String[].class);

        List<Integer> ids = (List<Integer>) method.invoke(service,
                new Object[]{new String[]{" 3 ", "", "3", null, "2"}});
        List<Integer> empty = (List<Integer>) method.invoke(service,
                new Object[]{new String[]{null, " "}});

        assertEquals(List.of(3, 2), ids);
        assertEquals(List.of(), empty);
    }

    @Test
    public void shouldBuildSqlPlaceholdersForIds() throws Exception {
        AdminCommentService service = new AdminCommentService();
        Method method = method("placeholders", int.class);

        assertEquals("", method.invoke(service, 0));
        assertEquals("?", method.invoke(service, 1));
        assertEquals("?,?,?", method.invoke(service, 3));
    }

    @Test
    public void shouldReturnFailureWhenReadRequestIsMissing() {
        UpdateRecordResponse response = new AdminCommentService().read(null);

        assertEquals(1, response.getError());
    }

    @Test
    public void shouldManageCommentsThroughRealDatabase() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedComments(db);
            AdminCommentService service = new AdminCommentService();
            ReadCommentRequest readRequest = new ReadCommentRequest();
            readRequest.setId(1L);

            UpdateRecordResponse readResponse = service.read(readRequest);
            int unreadAfterRead = service.countUnread();
            service.readAll();
            DeleteResponse deleteResponse = service.delete(new String[]{"2", "3"});
            DeleteResponse missingDeleteResponse = service.delete(new String[]{"999"});
            Map<String, Object> status = db.queryOne("select value from website where name=?",
                    "admin_cache:message_center_status");

            assertEquals(0, readResponse.getError());
            assertEquals(Boolean.TRUE, db.queryOne("select have_read from comment where commentId=?", 1)
                    .get("have_read"));
            assertEquals(1, unreadAfterRead);
            assertEquals(0, service.countUnread());
            assertEquals(0, deleteResponse.getError());
            assertTrue(deleteResponse.getData().getDelete());
            assertEquals(1L, db.scalar("select count(1) from comment"));
            assertEquals(1, missingDeleteResponse.getError());
            assertNotNull(status);
            assertNotNull(status.get("value"));
        }
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AdminCommentService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void seedComments(InMemoryZrLogDatabase db) throws Exception {
        db.execute("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1, "2026-06-29 09:00:00", false, "First", "first@example.com", "",
                "127.0.0.1", "First Reader", false, 1, "/first.png");
        db.execute("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                2, "2026-06-29 10:00:00", false, "Second", "second@example.com", "",
                "127.0.0.2", "Second Reader", false, 1, "/second.png");
        db.execute("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                3, "2026-06-29 11:00:00", true, "Third", "third@example.com", "",
                "127.0.0.3", "Third Reader", false, 1, "/third.png");
    }
}
