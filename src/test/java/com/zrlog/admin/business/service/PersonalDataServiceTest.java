package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalDataServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConvertCommentRowsToExportEntries() throws Exception {
        PersonalDataService service = new PersonalDataService();
        Method method = method("toCommentEntries", List.class);
        Map<String, Object> row = new HashMap<>();
        row.put("id", 9L);
        row.put("userComment", "hello");
        row.put("userMail", null);
        row.put("userHome", "https://example.com");
        row.put("userIp", "127.0.0.1");
        row.put("userName", "Alice");
        row.put("commTime", Timestamp.valueOf("2024-01-02 03:04:05"));
        row.put("logId", 12);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        rows.add(new HashMap<>());

        List<PersonalDataCommentExportResponse.CommentEntry> entries =
                (List<PersonalDataCommentExportResponse.CommentEntry>) method.invoke(service, rows);

        assertEquals(2, entries.size());
        PersonalDataCommentExportResponse.CommentEntry entry = entries.get(0);
        assertEquals(9L, entry.getId());
        assertEquals("hello", entry.getUserComment());
        assertEquals("", entry.getUserMail());
        assertEquals("https://example.com", entry.getUserHome());
        assertEquals("127.0.0.1", entry.getUserIp());
        assertEquals("Alice", entry.getUserName());
        assertEquals("2024-01-02 03:04:05", entry.getCommTime());
        assertEquals(12L, entry.getLogId());

        PersonalDataCommentExportResponse.CommentEntry empty = entries.get(1);
        assertEquals(0L, empty.getId());
        assertEquals("", empty.getUserComment());
        assertEquals("", empty.getCommTime());
        assertEquals(0L, empty.getLogId());
    }

    @Test
    public void shouldConvertHelperValuesSafely() throws Exception {
        PersonalDataService service = new PersonalDataService();
        Method toLong = method("toLong", Object.class);
        Method toString = method("toString", Object.class);
        Method formatDate = method("formatDate", Object.class);
        Method equalsIgnoreCase = method("equalsIgnoreCase", String.class, Object.class);

        assertEquals(7L, toLong.invoke(service, 7));
        assertEquals(0L, toLong.invoke(service, "7"));
        assertEquals("", toString.invoke(service, new Object[]{null}));
        assertEquals("42", toString.invoke(service, 42));
        assertEquals("", formatDate.invoke(service, new Object[]{null}));
        assertEquals("2024-01-02 03:04:05",
                formatDate.invoke(service, Timestamp.valueOf("2024-01-02 03:04:05")));
        assertTrue((Boolean) equalsIgnoreCase.invoke(service, "Admin", " admin "));
        assertFalse((Boolean) equalsIgnoreCase.invoke(service, "Admin", ""));
        assertFalse((Boolean) equalsIgnoreCase.invoke(service, "Admin", 1));
    }

    @Test
    public void shouldPreviewAndExportCommentMatchesThroughRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            insertComment(db, 1, "first", "Alice@Example.com", "https://alice.example", "10.0.0.1",
                    "Alice", "2024-02-01 10:00:00", 1);
            insertComment(db, 2, "second", "alice@example.com", "https://example.net", "10.0.0.2",
                    "Alice B", "2024-02-02 11:00:00", 2);
            insertComment(db, 3, "other", "other@example.com", "https://other.example", "10.0.0.3",
                    "Other", "2024-02-03 12:00:00", 2);
            PersonalDataService service = new PersonalDataService();

            PersonalDataPreviewResponse preview = service.preview(request("alice@example.com"), 1);
            PersonalDataPreviewResponse adminPreview = service.preview(request("admin@example.com"), 1);
            PersonalDataPreviewResponse anonymousPreview = service.preview(request("admin"), 0);
            PersonalDataCommentExportResponse export = service.exportComments(request("ALICE@example.com"));

            assertEquals("alice@example.com", preview.getQuery());
            assertEquals(2L, preview.getCommentCount());
            assertEquals(2L, preview.getCommentArticleCount());
            assertEquals("2024-02-02 11:00:00", preview.getLatestCommentTime());
            assertFalse(preview.isAdminUserMatched());
            assertFalse(preview.isAdminEmailMatched());
            assertTrue(preview.isPluginDataRequiresPlugin());

            assertTrue(adminPreview.isAdminEmailMatched());
            assertFalse(adminPreview.isAdminUserMatched());
            assertFalse(anonymousPreview.isAdminUserMatched());
            assertFalse(anonymousPreview.isAdminEmailMatched());

            assertEquals("ALICE@example.com", export.getQuery());
            assertEquals(2L, export.getCommentCount());
            assertTrue(export.getExportedAt() > 0);
            assertEquals(2, export.getComments().size());
            assertEquals(2L, export.getComments().get(0).getId());
            assertEquals("second", export.getComments().get(0).getUserComment());
            assertEquals("alice@example.com", export.getComments().get(0).getUserMail());
            assertEquals(2L, export.getComments().get(0).getLogId());
            assertEquals("2024-02-01 10:00:00", export.getComments().get(1).getCommTime());
        }
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = PersonalDataService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static PersonalDataPreviewRequest request(String query) {
        PersonalDataPreviewRequest request = new PersonalDataPreviewRequest();
        request.setQuery(query);
        return request;
    }

    private static void insertComment(InMemoryZrLogDatabase db, int id, String comment, String mail, String home,
                                      String ip, String name, String time, int logId) throws Exception {
        db.execute("insert into comment(commentId,commTime,hide,have_read,userComment,userMail,userHome,userIp,userName,"
                        + "logId) values(?,?,?,?,?,?,?,?,?,?)",
                id, Timestamp.valueOf(time), false, false, comment, mail, home, ip, name, logId);
    }
}
