package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.model.WebSite;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class MessageCenterStateServiceTest {

    @Test
    public void shouldParseStoredStatusAndFallbackForInvalidValues() throws Exception {
        MessageCenterStateService service =
                new MessageCenterStateService(
                        ApplicationServicesFixture.websiteCache(null), () -> {
                    throw new IllegalStateException("Database is unavailable in this test");
                });

        MessageCenterStatusResponse empty = service.parseStatus("");
        MessageCenterStatusResponse invalid = service.parseStatus("{bad-json");
        MessageCenterStatusResponse parsed = service.parseStatus("{\"revision\":42,\"hasUnread\":true}");

        assertEquals(0L, empty.getRevision());
        assertFalse(empty.isHasUnread());
        assertEquals(0L, invalid.getRevision());
        assertFalse(invalid.isHasUnread());
        assertEquals(42L, parsed.getRevision());
        assertTrue(parsed.isHasUnread());
    }

    @Test
    public void shouldCompareStatusValues() throws Exception {
        MessageCenterStateService service =
                new MessageCenterStateService(
                        ApplicationServicesFixture.websiteCache(null), () -> {
                    throw new IllegalStateException("Database is unavailable in this test");
                });

        assertTrue(service.sameStatus(
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(7L, true)));
        assertFalse(service.sameStatus(
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(8L, true)));
        assertFalse(service.sameStatus(
                new MessageCenterStatusResponse(7L, true),
                new MessageCenterStatusResponse(7L, false)));
    }

    @Test
    public void shouldGenerateMonotonicNextRevision() throws Exception {
        MessageCenterStateService service =
                new MessageCenterStateService(
                        ApplicationServicesFixture.websiteCache(null), () -> {
                    throw new IllegalStateException("Database is unavailable in this test");
                });
        long now = System.currentTimeMillis();

        long fromPast = service.nextRevision(now - 10_000);
        long fromFuture = service.nextRevision(now + 10_000);

        assertTrue(fromPast >= now);
        assertEquals(now + 10_001, fromFuture);
    }

    @Test
    public void shouldPersistAndSynchronizeStatusThroughWebsiteCache() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            MessageCenterStateService service =
                    new MessageCenterStateService(
                            ApplicationServicesFixture.websiteCache(db.dataSource()), () -> new WebSite(db.dataSource()));

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
}
