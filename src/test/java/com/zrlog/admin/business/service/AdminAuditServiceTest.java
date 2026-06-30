package com.zrlog.admin.business.service;

import com.zrlog.admin.business.type.AdminAuditAction;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdminAuditServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadOnlyValidAuditLogMapsFromJson() throws Exception {
        AdminAuditService service = new AdminAuditService();
        Method method = method("readLogs", String.class);

        assertTrue(((List<Map<String, Object>>) method.invoke(service, new Object[]{null})).isEmpty());
        assertTrue(((List<Map<String, Object>>) method.invoke(service, "not-json")).isEmpty());

        List<Map<String, Object>> logs = (List<Map<String, Object>>) method.invoke(service,
                "[{\"action\":\"REFRESH_CACHE\",\"timestamp\":1},\"invalid\",{\"type\":\"security\"}]");

        assertEquals(2, logs.size());
        assertEquals("REFRESH_CACHE", logs.get(0).get("action"));
        assertEquals("security", logs.get(1).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSanitizeSecurityLogsForDisplay() throws Exception {
        AdminAuditService service = new AdminAuditService();
        Method method = method("toDisplayLog", Map.class);

        Map<String, Object> byType = (Map<String, Object>) method.invoke(service,
                Map.of("action", "UNKNOWN", "type", "security", "content", "secret"));
        Map<String, Object> byAction = (Map<String, Object>) method.invoke(service,
                Map.of("action", "UPDATE_PASSWORD", "type", "system", "content", "secret"));
        Map<String, Object> regular = (Map<String, Object>) method.invoke(service,
                Map.of("action", "REFRESH_CACHE", "type", "system", "content", "cache"));

        assertEquals("", byType.get("content"));
        assertEquals("", byAction.get("content"));
        assertEquals("cache", regular.get("content"));
    }

    @Test
    public void shouldResolveAuditActionsAndUnknownValues() throws Exception {
        AdminAuditService service = new AdminAuditService();
        Method method = method("toAuditAction", Object.class);

        assertEquals(AdminAuditAction.REFRESH_CACHE, method.invoke(service, "REFRESH_CACHE"));
        assertEquals(null, method.invoke(service, "MISSING"));
        assertEquals(null, method.invoke(service, 1));
    }

    @Test
    public void shouldSanitizeSecurityActionContent() throws Exception {
        AdminAuditService service = new AdminAuditService();
        Method method = method("sanitizeContent", AdminAuditAction.class, String.class);

        assertEquals("", method.invoke(service, AdminAuditAction.UPDATE_PASSWORD, "secret"));
        assertEquals("", method.invoke(service, AdminAuditAction.REFRESH_CACHE, (Object) null));
        assertEquals("done", method.invoke(service, AdminAuditAction.REFRESH_CACHE, "done"));
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AdminAuditService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
