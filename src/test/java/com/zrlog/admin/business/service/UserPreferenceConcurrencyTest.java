package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import org.junit.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UserPreferenceConcurrencyTest {
    @Test public void personalSaveSurvivesConcurrentDashboardUpdates() throws Exception {
        savesAfterCompetingWrites(true);
    }

    @Test public void dashboardSaveSurvivesConcurrentPersonalUpdates() throws Exception {
        savesAfterCompetingWrites(false);
    }

    private void savesAfterCompetingWrites(boolean personal) throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AtomicInteger competingWrites = new AtomicInteger();
            db.beforeWebApiUpdate((sql, args) -> {
                if (!sql.startsWith("update user set preferences=") || competingWrites.get() >= 12) return;
                int revision = competingWrites.incrementAndGet();
                // Commit another instance's change between our read and conditional write.
                JsonObject latest = stored(db);
                latest.add("future", json("{\"key\":1}"));
                if (personal) latest.add("dashboard", json("{\"autoRefreshIntervalSeconds\":" + revision * 10 + "}"));
                else latest.addProperty("articlePageSize", revision);
                db.execute("update user set preferences=? where userId=1", latest.toString());
            });
            UserPreferenceService service = new UserPreferenceService();
            if (personal) service.update(json("{\"articlePageSize\":50}"));
            else {
                AdminDashboardConfigResponse dashboard = new AdminDashboardConfigResponse();
                dashboard.setAutoRefreshIntervalSeconds(90);
                service.saveDashboard(AdminTokenThreadLocal.getUser(), dashboard);
            }
            JsonObject saved = stored(db);
            assertEquals(12, competingWrites.get());
            assertEquals(personal ? 50 : 12, saved.get("articlePageSize").getAsInt());
            assertEquals(personal ? 120 : 90, saved.getAsJsonObject("dashboard").get("autoRefreshIntervalSeconds").getAsInt());
            assertEquals(1, saved.getAsJsonObject("future").get("key").getAsInt());
        }
    }

    @Test(timeout = 15000) public void sustainedContentionStopsWithoutOverwritingCompetingUpdates() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AtomicInteger competingWrites = new AtomicInteger();
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update user set preferences=")) {
                    db.execute("update user set preferences=? where userId=1",
                            "{\"future\":{\"revision\":" + competingWrites.incrementAndGet() + "}}");
                }
            });
            SQLException error = assertThrows(SQLException.class,
                    () -> new UserPreferenceService().update(json("{\"articlePageSize\":50}")));
            assertTrue(error.getMessage().contains("Concurrent account preference update"));
            JsonObject saved = stored(db);
            assertFalse(saved.has("articlePageSize"));
            assertEquals(competingWrites.get(), saved.getAsJsonObject("future").get("revision").getAsInt());
        }
    }

    @Test public void interruptedRetryPreservesInterruptAndCompetingUpdate() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update user set preferences=")) {
                    db.execute("update user set preferences=? where userId=1", "{\"future\":{\"key\":1}}");
                    Thread.currentThread().interrupt();
                }
            });
            try {
                SQLException error = assertThrows(SQLException.class,
                        () -> new UserPreferenceService().update(json("{\"articlePageSize\":50}")));
                assertTrue(error.getCause() instanceof InterruptedException);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertEquals(json("{\"future\":{\"key\":1}}"), stored(db));
        }
    }

    @Test public void databaseErrorsAreReportedWithoutRetrying() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            AtomicInteger writes = new AtomicInteger();
            SQLException unavailable = new SQLException("Database unavailable");
            db.beforeWebApiUpdate((sql, args) -> {
                if (sql.startsWith("update user set preferences=")) {
                    writes.incrementAndGet();
                    throw unavailable;
                }
            });
            assertSame(unavailable, assertThrows(SQLException.class,
                    () -> new UserPreferenceService().update(json("{\"articlePageSize\":50}"))));
            assertEquals(1, writes.get());
            assertEquals(new JsonObject(), stored(db));
        }
    }

    private static JsonObject stored(InMemoryZrLogDatabase db) throws SQLException {
        Object raw = db.scalar("select preferences from user where userId=1");
        return raw == null ? new JsonObject() : json(raw.toString());
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
