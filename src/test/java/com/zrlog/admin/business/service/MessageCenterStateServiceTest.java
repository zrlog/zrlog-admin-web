package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageCenterStateServiceTest {

    @Test
    public void shouldParseStoredStatusAndFallbackForInvalidValues() throws Exception {
        MessageCenterStateService service = new MessageCenterStateService();

        MessageCenterStatusResponse empty = parseStatus(service, "");
        MessageCenterStatusResponse invalid = parseStatus(service, "{bad-json");
        MessageCenterStatusResponse parsed = parseStatus(service, "{\"revision\":42,\"hasUnread\":true}");

        assertEquals(0L, empty.getRevision());
        assertFalse(empty.isHasUnread());
        assertEquals(0L, invalid.getRevision());
        assertFalse(invalid.isHasUnread());
        assertEquals(42L, parsed.getRevision());
        assertTrue(parsed.isHasUnread());
    }

    @Test
    public void shouldCompareStatusValues() throws Exception {
        MessageCenterStateService service = new MessageCenterStateService();

        assertTrue(sameStatus(service,
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(7L, true)));
        assertFalse(sameStatus(service,
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(8L, true)));
        assertFalse(sameStatus(service,
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(7L, false)));
    }

    @Test
    public void shouldGenerateMonotonicNextRevision() throws Exception {
        MessageCenterStateService service = new MessageCenterStateService();
        long now = System.currentTimeMillis();

        long fromPast = nextRevision(service, now - 10_000);
        long fromFuture = nextRevision(service, now + 10_000);

        assertTrue(fromPast >= now);
        assertEquals(now + 10_001, fromFuture);
    }

    @Test
    public void shouldPersistAndSynchronizeStatusThroughWebsiteCache() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            MessageCenterStateService service = new MessageCenterStateService();

            service.markMayHaveUnread();
            MessageCenterStatusResponse unread = service.current();
            service.syncActual(unread.getRevision(), false);
            MessageCenterStatusResponse cleared = service.current();
            service.markChanged();
            MessageCenterStatusResponse changed = service.current();
            service.syncActual(unread.getRevision(), false);
            MessageCenterStatusResponse unchanged = service.current();
            Map<String, Object> statusRow = db.queryOne("select value from website where name=?",
                    "admin_cache:message_center_status");
            MessageCenterStatusResponse stored = new Gson().fromJson(statusRow.get("value").toString(),
                    MessageCenterStatusResponse.class);

            assertTrue(unread.isHasUnread());
            assertFalse(cleared.isHasUnread());
            assertTrue(changed.getRevision() > cleared.getRevision());
            assertEquals(changed.getRevision(), unchanged.getRevision());
            assertEquals(changed.isHasUnread(), unchanged.isHasUnread());
            assertEquals(unchanged.getRevision(), stored.getRevision());
        }
    }

    private static MessageCenterStatusResponse parseStatus(MessageCenterStateService service, String raw)
            throws Exception {
        Method method = MessageCenterStateService.class.getDeclaredMethod("parseStatus", String.class);
        method.setAccessible(true);
        return (MessageCenterStatusResponse) method.invoke(service, raw);
    }

    private static boolean sameStatus(MessageCenterStateService service, MessageCenterStatusResponse current,
                                      MessageCenterStatusResponse next) throws Exception {
        Method method = MessageCenterStateService.class.getDeclaredMethod(
                "sameStatus", MessageCenterStatusResponse.class, MessageCenterStatusResponse.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, current, next);
    }

    private static long nextRevision(MessageCenterStateService service, long currentRevision) throws Exception {
        Method method = MessageCenterStateService.class.getDeclaredMethod("nextRevision", long.class);
        method.setAccessible(true);
        return (Long) method.invoke(service, currentRevision);
    }
}
