package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminAuditServiceTest {

    @Test
    public void shouldReadOnlyValidAuditLogEntriesFromJson() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertTrue(service.readLogs(null).isEmpty());
        assertTrue(service.readLogs("not-json").isEmpty());

        List<AdminAuditLogEntryResponse> logs = service.readLogs(
                "[{\"action\":\"REFRESH_CACHE\",\"timestamp\":123.0,\"crawler\":\"true\"},\"invalid\",{\"type\":\"security\"}]");

        assertEquals(2, logs.size());
        assertEquals("REFRESH_CACHE", logs.get(0).getAction());
        assertEquals(Long.valueOf(123L), logs.get(0).getTimestamp());
        assertEquals(Boolean.TRUE, logs.get(0).getCrawler());
        assertEquals("security", logs.get(1).getType());
    }

    @Test
    public void shouldSanitizeSecurityLogsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService();

        AdminAuditLogEntryResponse byType = service.toDisplayLog(entry("UNKNOWN", "security", "secret"));
        AdminAuditLogEntryResponse byAction = service.toDisplayLog(entry("UPDATE_PASSWORD", "system", "secret"));
        AdminAuditLogEntryResponse regular = service.toDisplayLog(entry("REFRESH_CACHE", "system", "cache"));

        assertEquals("", byType.getContent());
        assertEquals("", byAction.getContent());
        assertEquals("cache", regular.getContent());
    }

    @Test
    public void shouldConvertAuditLogFieldsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService();

        AdminAuditLogEntryResponse raw = entry("UNKNOWN", "system", "done");
        raw.setTimestamp(123L);
        raw.setIp("127.0.0.1");
        raw.setOs("Linux");
        raw.setBrowser("Chrome");
        raw.setCrawler(true);
        AdminAuditLogEntryResponse entry = service.toDisplayLog(raw);

        assertEquals(Long.valueOf(123L), entry.getTimestamp());
        assertEquals("127.0.0.1", entry.getIp());
        assertEquals("UNKNOWN", entry.getAction());
        assertEquals("system", entry.getType());
        assertEquals("done", entry.getContent());
        assertEquals("Linux", entry.getOs());
        assertEquals("Chrome", entry.getBrowser());
        assertEquals(Boolean.TRUE, entry.getCrawler());
    }

    @Test
    public void shouldResolveAuditActionsAndUnknownValues() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertEquals(AdminAuditAction.REFRESH_CACHE, service.toAuditAction("REFRESH_CACHE"));
        assertEquals(null, service.toAuditAction("MISSING"));
        assertEquals(null, service.toAuditAction(null));
    }

    @Test
    public void shouldSanitizeSecurityActionContent() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertEquals("", service.sanitizeContent(AdminAuditAction.UPDATE_PASSWORD, "secret"));
        assertEquals("", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, null));
        assertEquals("done", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, "done"));
    }

    private static AdminAuditLogEntryResponse entry(String action, String type, String content) {
        AdminAuditLogEntryResponse entry = new AdminAuditLogEntryResponse();
        entry.setAction(action);
        entry.setType(type);
        entry.setContent(content);
        return entry;
    }
}
