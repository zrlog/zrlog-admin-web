package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.support.ApplicationServicesFixture;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminAuditServiceTest {

    private static AdminAuditLogEntryResponse entry(String action, String type, String content) {
        AdminAuditLogEntryResponse entry = new AdminAuditLogEntryResponse();
        entry.setAction(action);
        entry.setType(type);
        entry.setContent(content);
        return entry;
    }

    @Test
    public void shouldReadOnlyValidAuditLogEntriesFromJson() throws Exception {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));

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
    public void shouldReadAgentFieldsAndKeepLegacyLogsCompatible() {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));
        List<AdminAuditLogEntryResponse> logs = service.readLogs(
                "[{\"browser\":\"ZrLogCtl 0.1.42\",\"agentName\":\"dot\",\"agentVersion\":\"1.2\"},{\"browser\":\"Chrome\"}]");
        AdminAuditLogEntryResponse display = service.toDisplayLog(logs.get(0));
        assertEquals("ZrLogCtl 0.1.42", display.getBrowser());
        assertEquals("dot", display.getAgentName());
        assertEquals("1.2", display.getAgentVersion());
        assertEquals(null, logs.get(1).getAgentName());
        assertEquals(null, logs.get(1).getAgentVersion());
    }

    @Test
    public void shouldSanitizeSecurityLogsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));

        AdminAuditLogEntryResponse byType = service.toDisplayLog(entry("UNKNOWN", "security", "secret"));
        AdminAuditLogEntryResponse byAction = service.toDisplayLog(entry("UPDATE_PASSWORD", "system", "secret"));
        AdminAuditLogEntryResponse regular = service.toDisplayLog(entry("REFRESH_CACHE", "system", "cache"));

        assertEquals("", byType.getContent());
        assertEquals("", byAction.getContent());
        assertEquals("cache", regular.getContent());
    }

    @Test
    public void shouldConvertAuditLogFieldsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));

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
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));

        assertEquals(AdminAuditAction.REFRESH_CACHE, service.toAuditAction("REFRESH_CACHE"));
        assertEquals(null, service.toAuditAction("MISSING"));
        assertEquals(null, service.toAuditAction(null));
    }

    @Test
    public void shouldSanitizeSecurityActionContent() throws Exception {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));

        assertEquals("", service.sanitizeContent(AdminAuditAction.UPDATE_PASSWORD, "secret"));
        assertEquals("", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, null));
        assertEquals("done", service.sanitizeContent(AdminAuditAction.REFRESH_CACHE, "done"));
    }
}
