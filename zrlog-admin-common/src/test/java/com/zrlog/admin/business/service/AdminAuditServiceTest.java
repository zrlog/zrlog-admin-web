package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.support.ApplicationServicesFixture;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminAuditServiceTest {

    @Test
    public void existingAssistantReferencesBecomeLinksWithoutChangingStoredHistory() {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));
        String runId = "0000000000.00000001793405016977.2e28cb7f-7ecb-4545-908c-085adaf3e485";
        for (var action : List.of(AdminAuditAction.EXECUTE_UPGRADE, AdminAuditAction.SYNC_ADMIN_STATIC_SITE)) {
            String prefix = action == AdminAuditAction.EXECUTE_UPGRADE ? "assistant:" : "upgrade:";
            var raw = entry(action.name(), "system", prefix + runId);
            raw.setActorUserId(1);
            raw.setBrowser("Assistant");
            raw.setOs("Unknown");
            raw.setAgentName("ZrLog Assistant");
            var display = service.toDisplayLog(raw);
            assertEquals(runId, display.getAssistantRunId());
            assertEquals(Integer.valueOf(1), display.getActorUserId());
            assertEquals("", display.getContent());
            assertEquals(null, display.getOs());
            assertEquals(null, display.getBrowser());
            assertEquals(null, display.getAgentName());
            assertEquals(prefix + runId, raw.getContent());
        }
        var article = entry("CREATE_ARTICLE", "article", "assistant:" + runId);
        article.setBrowser("Assistant");
        assertEquals(article.getContent(), service.toDisplayLog(article).getContent());
        assertEquals(null, service.toDisplayLog(article).getAssistantRunId());
    }

    @Test
    public void assistantAuditReferenceSurvivesReadAndDisplayWithoutReplacingArticleTitle() {
        AdminAuditService service = new AdminAuditService(ApplicationServicesFixture.websiteKv(null));
        var raw = service.readLogs("[{\"action\":\"CREATE_ARTICLE\",\"type\":\"article\","
                + "\"content\":\"Scheduled article\",\"assistantRunId\":\"article-run\",\"actorUserId\":2}]").get(0);
        var display = service.toDisplayLog(raw);
        assertEquals("article-run", display.getAssistantRunId());
        assertEquals("Scheduled article", display.getContent());
        assertEquals(Integer.valueOf(2), display.getActorUserId());
    }

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
        var upgrade = entry("EXECUTE_UPGRADE", "system", "upgradeRiskAccepted");
        assertEquals("", service.toDisplayLog(upgrade).getContent());
        assertEquals("upgradeRiskAccepted", upgrade.getContent());
        assertEquals("upgradeRiskAccepted", service.toDisplayLog(
                entry("CREATE_ARTICLE", "article", "upgradeRiskAccepted")).getContent());
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
