package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.business.rest.response.UserPreferencesResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class UserPreferenceServiceTest {
    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() { return new Boolean[]{false, true}; }
    private final boolean sqlite;
    public UserPreferenceServiceTest(boolean sqlite) { this.sqlite = sqlite; }
    private InMemoryZrLogDatabase database() throws Exception {
        return sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }
    private JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private void login(int id) throws Exception {
        AdminTokenVO token = new AdminTokenVO(); token.setUserId(id); token.setSessionId("test-session-" + id);
        java.lang.reflect.Method setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true); AdminTokenThreadLocal.remove(); setter.invoke(null, token);
    }

    @Test public void isolatedOverridesInheritResetAndKeepAuthentication() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','contributor')");
            db.putWebsite("admin_article_page_size", 20);
            db.putWebsite("article_edit_auto_save_interval", 5);
            UserPreferenceService service = new UserPreferenceService();
            service.update(json("{\"language\":\"en_US\",\"appearance\":{\"darkMode\":true},\"articlePageSize\":40,\"editor\":{\"autoSaveInterval\":10}}"));
            assertEquals(40, new AdminArticleService().resolveAdminPageSize(0));
            assertEquals(7, new AdminArticleService().resolveAdminPageSize(7));
            assertEquals(Long.valueOf(10), service.effective().editor.autoSaveInterval);
            assertTrue(service.effective().appearance.darkMode);
            assertEquals("20", db.scalar("select value from website where name='admin_article_page_size'"));
            assertEquals(0, ((Number) db.scalar("select authVersion from user where userId=1")).intValue());
            assertEquals("admin", AccountPermissionService.current().getRole());
            login(2);
            assertEquals(20, new AdminArticleService().resolveAdminPageSize(0));
            service.update(json("{\"articlePageSize\":15}"));
            assertEquals(15, new AdminArticleService().resolveAdminPageSize(0));
            login(1);
            assertEquals(40, new AdminArticleService().resolveAdminPageSize(0));
            service.update(json("{}"));
            assertEquals(20, new AdminArticleService().resolveAdminPageSize(0));
            db.putWebsite("admin_article_page_size", 30);
            assertEquals(30, new AdminArticleService().resolveAdminPageSize(0));
            login(2);
            assertEquals(15, new AdminArticleService().resolveAdminPageSize(0));
        }
    }

    @Test public void assistantDefaultsAndSingleScopeAreAccountSpecific() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            UserPreferenceService service = new UserPreferenceService();
            assertEquals("own_public", service.current().effective.assistant.knowledgeScope);
            service.update(json("{\"assistant\":{\"knowledgeScope\":\"own_all\"}}"));
            assertEquals("own_all", service.assistant(AdminTokenThreadLocal.getUser()).knowledgeScope);
            login(2);
            assertEquals("own_public", service.assistant(AdminTokenThreadLocal.getUser()).knowledgeScope);
            service.update(json("{\"assistant\":{\"knowledgeScope\":\"off\"}}"));
            assertEquals("off", service.current().effective.assistant.knowledgeScope);
            login(1);
            assertEquals("own_all", service.current().effective.assistant.knowledgeScope);
            service.update(json("{}"));
            assertEquals("own_public", service.current().effective.assistant.knowledgeScope);
        }
    }

    @Test public void validatesTypesRangesAndRejectsAuthorizationFields() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            UserPreferenceService service = new UserPreferenceService();
            for (String invalid : List.of("{\"userId\":2}", "{\"role\":\"owner\"}", "{\"scope\":\"articles:read:private\"}",
                    "{\"dashboard\":{}}", "{\"language\":\"xx\"}", "{\"language\":1}", "{\"appearance\":[]}",
                    "{\"appearance\":{\"darkMode\":\"true\"}}", "{\"appearance\":{\"theme\":\"invalid\"}}",
                    "{\"appearance\":{\"colorPrimary\":\"red\"}}", "{\"articlePageSize\":0}", "{\"articlePageSize\":101}",
                    "{\"articlePageSize\":1.1}", "{\"articlePageSize\":\"10\"}", "{\"editor\":{\"autoSaveInterval\":30}}",
                    "{\"editor\":{\"publishCheck\":false}}", "{\"assistant\":{\"knowledgeScope\":true}}",
                    "{\"assistant\":{\"knowledgeScope\":\"everything\"}}", "{\"assistant\":{\"role\":\"owner\"}}")) {
                assertThrows(invalid, ArgsException.class, () -> service.update(json(invalid)));
            }
            for (String invalid : List.of("[]", "null", "true", "{broken")) {
                assertThrows(ArgsException.class, () -> service.updateBody(invalid));
            }
            assertNull(db.scalar("select preferences from user where userId=1"));
            service.update(json("{\"appearance\":{\"darkMode\":false},\"language\":null}"));
            assertEquals(Boolean.FALSE, service.current().overrides.appearance.darkMode);
            db.execute("update user set enabled=false where userId=1");
            assertThrows(PermissionErrorException.class, () -> service.update(json("{}")));
        }
    }

    @Test public void preservesDashboardAndFutureSectionsAndRecoversCorruptStorage() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            UserPreferenceService service = new UserPreferenceService();
            db.execute("update user set preferences=? where userId=1", "{\"future\":{\"key\":1},\"dashboard\":{\"autoRefreshEnabled\":true}}");
            service.update(json("{\"articlePageSize\":50}"));
            assertTrue(service.dashboard(AdminTokenThreadLocal.getUser()).getAutoRefreshEnabled());
            AdminDashboardConfigResponse dashboard = new AdminDashboardConfigResponse();
            dashboard.setAutoRefreshIntervalSeconds(120);
            service.saveDashboard(AdminTokenThreadLocal.getUser(), dashboard);
            assertEquals(Integer.valueOf(50), service.effective().articlePageSize);
            JsonObject stored = json(db.scalar("select preferences from user where userId=1").toString());
            assertTrue(stored.has("future"));
            UserPreferencesResponse response = service.current();
            assertFalse(new Gson().toJson(response).contains("dashboard"));
            assertNull(service.dashboard(null));
            db.execute("update user set preferences=? where userId=1", "{broken");
            assertNull(service.current().overrides.articlePageSize);
            service.update(json("{\"articlePageSize\":25}"));
            assertEquals(Integer.valueOf(25), service.effective().articlePageSize);
        }
    }
    @Test public void concurrentSectionsDoNotOverwriteEachOther() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            try {
                java.util.concurrent.Future<?> personal = pool.submit(() -> {
                    try {
                        login(1); start.await();
                        for (int i = 1; i <= 10; i++) new UserPreferenceService().update(json("{\"articlePageSize\":" + i + "}"));
                    } catch (Exception e) { throw new RuntimeException(e); }
                    finally { AdminTokenThreadLocal.remove(); }
                });
                java.util.concurrent.Future<?> dashboard = pool.submit(() -> {
                    try {
                        login(1); start.await();
                        for (int i = 1; i <= 10; i++) {
                            AdminDashboardConfigResponse config = new AdminDashboardConfigResponse();
                            config.setAutoRefreshIntervalSeconds(i * 10);
                            new UserPreferenceService().saveDashboard(AdminTokenThreadLocal.getUser(), config);
                        }
                    } catch (Exception e) { throw new RuntimeException(e); }
                    finally { AdminTokenThreadLocal.remove(); }
                });
                start.countDown(); personal.get(10, java.util.concurrent.TimeUnit.SECONDS); dashboard.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(Integer.valueOf(10), new UserPreferenceService().effective().articlePageSize);
                assertEquals(Integer.valueOf(100), new UserPreferenceService().dashboard(AdminTokenThreadLocal.getUser()).getAutoRefreshIntervalSeconds());
            } finally { pool.shutdownNow(); }
        }
    }

}
