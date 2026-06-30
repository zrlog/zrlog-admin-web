package com.zrlog.admin.business.service;

import com.zrlog.admin.business.type.AdminAuditAction;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminAuditServiceTest {

    @Test
    public void shouldReadOnlyValidAuditLogMapsFromJson() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertTrue(service.readLogs(null).isEmpty());
        assertTrue(service.readLogs("not-json").isEmpty());

        List<Map<String, Object>> logs = service.readLogs(
                "[{\"action\":\"REFRESH_CACHE\",\"timestamp\":1},\"invalid\",{\"type\":\"security\"}]");

        assertEquals(2, logs.size());
        assertEquals("REFRESH_CACHE", logs.get(0).get("action"));
        assertEquals("security", logs.get(1).get("type"));
    }

    @Test
    public void shouldSanitizeSecurityLogsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService();

        Map<String, Object> byType = service.toDisplayLog(
                Map.of("action", "UNKNOWN", "type", "security", "content", "secret"));
        Map<String, Object> byAction = service.toDisplayLog(
                Map.of("action", "UPDATE_PASSWORD", "type", "system", "content", "secret"));
        Map<String, Object> regular = service.toDisplayLog(
                Map.of("action", "REFRESH_CACHE", "type", "system", "content", "cache"));

        assertEquals("", byType.get("content"));
        assertEquals("", byAction.get("content"));
        assertEquals("cache", regular.get("content"));
    }

    @Test
    public void shouldResolveAuditActionsAndUnknownValues() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertEquals(AdminAuditAction.REFRESH_CACHE, service.toAuditAction("REFRESH_CACHE"));
        assertEquals(null, service.toAuditAction("MISSING"));
        assertEquals(null, service.toAuditAction(1));
    }

    @Test
    public void shouldSanitizeSecurityActionContent() throws Exception {
        AdminAuditService service = new AdminAuditService();

        assertEquals("", service.sanitizeContent(AdminAuditAction.UPDATE_PASSWORD, "secret"));
        assertEquals("", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, null));
        assertEquals("done", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, "done"));
    }
}
