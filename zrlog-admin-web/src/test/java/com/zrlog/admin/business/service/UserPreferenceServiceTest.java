package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.business.rest.response.UserPreferencesResponse;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class UserPreferenceServiceTest {
    private final boolean sqlite;

    public UserPreferenceServiceTest(boolean sqlite) {
        this.sqlite = sqlite;
    }

    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() {
        return new Boolean[]{false, true};
    }

    private static com.zrlog.data.security.AccountAccess actor(InMemoryZrLogDatabase db) {
        return AccountServicesFixture.component(db.dataSource()).requestIdentity().current();
    }

    private InMemoryZrLogDatabase database() throws Exception {
        return sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }

    private JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private void login(int id) throws Exception {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(id);
        token.setSessionId("test-session-" + id);
        java.lang.reflect.Method setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true);
        AdminTokenThreadLocal.remove();
        setter.invoke(null, token);
    }

    @Test
    public void isolatedOverridesInheritResetAndKeepAuthentication() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','contributor')");
            db.putWebsite("admin_article_page_size", 20);
            db.putWebsite("article_edit_auto_save_interval", 5);
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            service.update(actor(db), json("{\"language\":\"en_US\",\"appearance\":{\"darkMode\":true},\"articlePageSize\":40,\"editor\":{\"autoSaveInterval\":10}}"));
            assertEquals(
                    40, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            assertEquals(7, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 7));
            assertEquals(Long.valueOf(10), service.effective(actor(db)).editor.autoSaveInterval);
            assertTrue(service.effective(actor(db)).appearance.darkMode);
            assertEquals("20", db.scalar("select value from website where name='admin_article_page_size'"));
            assertEquals(0, ((Number) db.scalar("select authVersion from user where userId=1")).intValue());
            assertEquals(
                    "admin", actor(db).getRole());
            login(2);
            assertEquals(
                    20, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            service.update(actor(db), json("{\"articlePageSize\":15}"));
            assertEquals(
                    15, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            login(1);
            assertEquals(
                    40, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            service.update(actor(db), json("{}"));
            assertEquals(
                    20, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            db.putWebsite("admin_article_page_size", 30);
            assertEquals(
                    30, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
            login(2);
            assertEquals(
                    15, db.component(ContentComponent.class).queries().resolveAdminPageSize(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), 0));
        }
    }

    @Test
    public void extendedPreferencesAreIsolatedInheritedAndLegacySafe() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.putWebsite("session_timeout", 90);
            db.putWebsite("article_auto_digest_length", 250);
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            service.updateBody(actor(db), "{\"session\":{\"timeoutMinutes\":60}}", "session");
            service.updateBody(actor(db), "{\"editor\":{\"autoDigestLength\":0,\"linkPreviewEnabled\":true,\"publishCheckEnabled\":false,\"coverAspectRatio\":\"1:1\"},\"articleList\":{\"status\":\"draft\",\"sort\":\"click,ASC\",\"columns\":[]}}", "writing");
            assertEquals(Long.valueOf(0), service.effective(actor(db)).editor.autoDigestLength);
            assertFalse(service.effective(actor(db)).editor.publishCheckEnabled);
            assertEquals("1:1", service.effective(actor(db)).editor.coverAspectRatio);
            assertEquals("draft", service.effective(actor(db)).articleList.status);
            assertTrue(service.effective(actor(db)).articleList.columns.isEmpty());
            assertEquals(60, new UserPreferenceStore(() -> new User(db.dataSource())).sessionTimeoutMinutes(1, 90));
            // A stale appearance page must not write its stale session/editor snapshot.
            service.updateBody(actor(db), "{\"appearance\":{\"darkMode\":true},\"session\":{\"timeoutMinutes\":10},\"editor\":{\"autoDigestLength\":99}}", "appearance");
            assertEquals(Long.valueOf(60), service.effective(actor(db)).session.timeoutMinutes);
            assertEquals(Long.valueOf(0), service.effective(actor(db)).editor.autoDigestLength);
            // Old clients cannot erase fields introduced after their version.
            service.updateBody(actor(db), "{\"editor\":{\"autoSaveInterval\":10}}");
            assertEquals(Long.valueOf(0), service.effective(actor(db)).editor.autoDigestLength);
            assertEquals(Long.valueOf(60), service.effective(actor(db)).session.timeoutMinutes);
            assertEquals("draft", service.effective(actor(db)).articleList.status);
            login(2);
            assertEquals(Long.valueOf(90), service.effective(actor(db)).session.timeoutMinutes);
            assertEquals(Long.valueOf(250), service.effective(actor(db)).editor.autoDigestLength);
            assertEquals("", service.effective(actor(db)).articleList.status);
            login(1);
            service.updateBody(actor(db), "{}", "writing");
            assertEquals(Long.valueOf(250), service.effective(actor(db)).editor.autoDigestLength);
            assertEquals(Long.valueOf(60), service.effective(actor(db)).session.timeoutMinutes);
            service.updateBody(actor(db), "{}", "session");
            db.putWebsite("session_timeout", 120);
            assertEquals(Long.valueOf(120), service.effective(actor(db)).session.timeoutMinutes);
            assertEquals(0, ((Number) db.scalar("select authVersion from user where userId=1")).intValue());
        }
    }

    @Test
    public void validatesExtendedFieldsAndKeepsValidPartitionsWhenAnotherIsCorrupt() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            for (String invalid : List.of(
                    "{\"session\":{\"timeoutMinutes\":5}}", "{\"session\":{\"timeoutMinutes\":100000}}",
                    "{\"session\":{\"timeoutMinutes\":1.5}}", "{\"session\":{\"timeoutMinutes\":\"60\"}}",
                    "{\"editor\":{\"autoDigestLength\":-2}}", "{\"editor\":{\"autoDigestLength\":100000}}",
                    "{\"editor\":{\"coverAspectRatio\":\"bad\"}}", "{\"editor\":{\"publishCheckEnabled\":1}}",
                    "{\"articleList\":{\"sort\":\"secretKey,DESC\"}}", "{\"articleList\":{\"status\":\"all\"}}",
                    "{\"articleList\":{\"columns\":[\"click\",\"click\"]}}", "{\"articleList\":{\"columns\":[\"secretKey\"]}}")) {
                assertThrows(invalid, ArgsException.class, () -> service.updateBody(actor(db), invalid));
            }
            assertThrows(ArgsException.class, () -> service.updateBody(actor(db), "{}", "unknown"));
            db.execute("update user set preferences=? where userId=1", "{\"appearance\":{\"theme\":\"broken\"},\"session\":{\"timeoutMinutes\":60}}");
            assertEquals(Long.valueOf(60), service.current(actor(db)).effective.session.timeoutMinutes);
            assertNull(service.current(actor(db)).overrides.appearance);
        }
    }

    @Test
    public void assistantDefaultsAndSingleScopeAreAccountSpecific() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            assertEquals("own_public", service.current(actor(db)).effective.assistant.knowledgeScope);
            service.update(actor(db), json("{\"assistant\":{\"knowledgeScope\":\"own_all\"}}"));
            assertEquals("own_all", service.assistant(actor(db)).knowledgeScope);
            login(2);
            assertEquals("own_public", service.assistant(actor(db)).knowledgeScope);
            service.update(actor(db), json("{\"assistant\":{\"knowledgeScope\":\"off\"}}"));
            assertEquals("off", service.current(actor(db)).effective.assistant.knowledgeScope);
            login(1);
            assertEquals("own_all", service.current(actor(db)).effective.assistant.knowledgeScope);
            service.update(actor(db), json("{}"));
            assertEquals("own_public", service.current(actor(db)).effective.assistant.knowledgeScope);
        }
    }

    @Test
    public void validatesTypesRangesAndRejectsAuthorizationFields() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            for (String invalid : List.of("{\"userId\":2}", "{\"role\":\"owner\"}", "{\"scope\":\"articles:read:private\"}",
                    "{\"dashboard\":{}}", "{\"language\":\"xx\"}", "{\"language\":1}", "{\"appearance\":[]}",
                    "{\"appearance\":{\"darkMode\":\"true\"}}", "{\"appearance\":{\"theme\":\"invalid\"}}",
                    "{\"appearance\":{\"colorPrimary\":\"red\"}}", "{\"articlePageSize\":0}", "{\"articlePageSize\":101}",
                    "{\"articlePageSize\":1.1}", "{\"articlePageSize\":\"10\"}", "{\"editor\":{\"autoSaveInterval\":30}}",
                    "{\"editor\":{\"publishCheck\":false}}", "{\"assistant\":{\"knowledgeScope\":true}}",
                    "{\"assistant\":{\"knowledgeScope\":\"everything\"}}", "{\"assistant\":{\"role\":\"owner\"}}")) {
                assertThrows(invalid, ArgsException.class, () -> service.update(actor(db), json(invalid)));
            }
            for (String invalid : List.of("[]", "null", "true", "{broken")) {
                assertThrows(ArgsException.class, () -> service.updateBody(actor(db), invalid));
            }
            assertNull(db.scalar("select preferences from user where userId=1"));
            service.update(actor(db), json("{\"appearance\":{\"darkMode\":false},\"language\":null}"));
            assertEquals(Boolean.FALSE, service.current(actor(db)).overrides.appearance.darkMode);
            db.execute("update user set enabled=false where userId=1");
            assertThrows(PermissionErrorException.class, () -> service.update(actor(db), json("{}")));
        }
    }

    @Test
    public void preservesDashboardAndFutureSectionsAndRecoversCorruptStorage() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            UserPreferenceService service =
                    db.component(AccountComponent.class).preferences();
            db.execute("update user set preferences=? where userId=1", "{\"future\":{\"key\":1},\"dashboard\":{\"autoRefreshEnabled\":true}}");
            service.update(actor(db), json("{\"articlePageSize\":50}"));
            assertTrue(service.dashboard(actor(db)).getAutoRefreshEnabled());
            AdminDashboardConfigResponse dashboard = new AdminDashboardConfigResponse();
            dashboard.setAutoRefreshIntervalSeconds(120);
            service.saveDashboard(actor(db), dashboard);
            assertEquals(Integer.valueOf(50), service.effective(actor(db)).articlePageSize);
            JsonObject stored = json(db.scalar("select preferences from user where userId=1").toString());
            assertTrue(stored.has("future"));
            UserPreferencesResponse response = service.current(actor(db));
            assertFalse(new Gson().toJson(response).contains("dashboard"));
            assertNull(service.dashboard(null));
            db.execute("update user set preferences=? where userId=1", "{broken");
            assertNull(service.current(actor(db)).overrides.articlePageSize);
            service.update(actor(db), json("{\"articlePageSize\":25}"));
            assertEquals(Integer.valueOf(25), service.effective(actor(db)).articlePageSize);
        }
    }

    @Test
    public void concurrentSectionsDoNotOverwriteEachOther() throws Exception {
        try (InMemoryZrLogDatabase db = database()) {
            db.loadAdminWebModules();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> personal =
                        pool.submit(
                                () -> {
                                    try {
                                        login(1);
                                        start.await();
                                        for (int i = 1; i <= 10; i++)
                                            db.component(AccountComponent.class).preferences()
                                                    .update(actor(db), json(
                                                            "{\"articlePageSize\":"
                                                                    + i
                                                                    + "}"));
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    } finally {
                                        AdminTokenThreadLocal.remove();
                                    }
                                });
                Future<?> dashboard =
                        pool.submit(
                                () -> {
                                    try {
                                        login(1);
                                        start.await();
                                        for (int i = 1; i <= 10; i++) {
                                            AdminDashboardConfigResponse config =
                                                    new AdminDashboardConfigResponse();
                                            config.setAutoRefreshIntervalSeconds(i * 10);
                                            db.component(AccountComponent.class).preferences()
                                                    .saveDashboard(actor(db),
                                                            config);
                                        }
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    } finally {
                                        AdminTokenThreadLocal.remove();
                                    }
                                });
                start.countDown();
                personal.get(10, TimeUnit.SECONDS);
                dashboard.get(10, TimeUnit.SECONDS);
                assertEquals(
                        Integer.valueOf(10),
                        db.component(AccountComponent.class).preferences()
                                .effective(actor(db))
                                .articlePageSize);
                assertEquals(
                        Integer.valueOf(100),
                        db.component(AccountComponent.class).preferences()
                                .dashboard(actor(db))
                                .getAutoRefreshIntervalSeconds());
            } finally {
                pool.shutdownNow();
            }
        }
    }

}
