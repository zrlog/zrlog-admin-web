package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.hibegin.common.dao.DAO;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.model.UserPasskey;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

@RunWith(Parameterized.class)
public class AccountProfileServiceDatabaseTest {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> databases() {
        return List.of("h2", "sqlite");
    }

    private final String database;

    public AccountProfileServiceDatabaseTest(String database) {
        this.database = database;
    }

    @Test
    public void profileReadsAndUpdatesKeepPasskeysOnTheirAccountAndApplication() throws Exception {
        try (InMemoryZrLogDatabase first = open(); InMemoryZrLogDatabase second = open()) {
            first.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            first.execute("update user set preferences=?,mfaEnabled=1 where userId=1",
                    "{\"language\":\"en_US\",\"appearance\":{\"theme\":\"desk\"}}");
            addPasskey(first, 1, "Owner key");
            addPasskey(first, 2, "Writer key");
            addPasskey(second, 1, "Other application key");
            var firstGraph = AccountServicesFixture.component(first.dataSource());
            var secondGraph = AccountServicesFixture.component(second.dataSource());
            org.junit.Assert.assertNull(DAO.getDefaultDataSource());
            AdminTokenThreadLocal.remove();

            AccountProfileService profiles = firstGraph.profiles();
            UserBasicInfoResponse owner = profiles.getBasicUserInfo(1, "owner-session");
            assertEquals("admin", owner.getUserName());
            assertEquals("admin@example.com", owner.getEmail());
            assertEquals("owner-session", owner.getKey());
            assertEquals("en_US", owner.getLanguage());
            assertEquals("desk", owner.getAppearance().theme);
            assertTrue(owner.isMfaEnabled());
            assertPasskey(owner, "Owner key");
            UserBasicInfoResponse writer = profiles.getBasicUserInfo(2, "writer-session");
            assertEquals("author", writer.getRole());
            assertEquals("writer-session", writer.getKey());
            assertPasskey(writer, "Writer key");
            assertPasskey(secondGraph.profiles().getBasicUserInfo(1, "other-session"),
                    "Other application key");

            UpdateAdminRequest input = new UpdateAdminRequest();
            input.setUserName("renamed");
            input.setEmail("renamed@example.com");
            input.setHeader("/attached/new-avatar.png");
            UserBasicInfoResponse updated = profiles.update(1, "explicit-session", input, null, null);
            assertEquals("renamed", updated.getUserName());
            assertEquals("renamed@example.com", updated.getEmail());
            assertEquals("/attached/new-avatar.png", updated.getHeader());
            assertEquals("explicit-session", updated.getKey());
            assertEquals("en_US", updated.getLanguage());
            assertPasskey(updated, "Owner key");
            assertEquals("renamed", first.scalar("select userName from user where userId=1"));
            assertEquals("writer", first.scalar("select userName from user where userId=2"));
            assertEquals("admin", second.scalar("select userName from user where userId=1"));
            assertEquals("admin", owner.getUserName());
            assertNull(AdminTokenThreadLocal.getUser());

            var json = new Gson().toJsonTree(updated).getAsJsonObject();
            assertFalse(json.has("password"));
            assertFalse(json.has("secretKey"));
            assertEquals("Owner key", json.getAsJsonArray("passkeys").get(0)
                    .getAsJsonObject().get("name").getAsString());
            first.execute("delete from user_passkey where userId=1");
            assertTrue(profiles.getBasicUserInfo(1, "next-session").getPasskeys().isEmpty());
            assertPasskey(profiles.getBasicUserInfo(2, "writer-session"), "Writer key");
            assertPasskey(secondGraph.profiles().getBasicUserInfo(1, "other-session"),
                    "Other application key");
        }
    }

    private InMemoryZrLogDatabase open() throws Exception {
        return "sqlite".equals(database) ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
    }

    private static void addPasskey(InMemoryZrLogDatabase db, int userId, String name) throws Exception {
        assertTrue(new UserPasskey(db.dataSource()).save(userId, "hash-" + userId,
                "credential-" + userId, "public-key", 0, "internal", name, "aaguid", true, false,
                "https://example.com", "example.com", 1_700_000_000_000L));
    }

    private static void assertPasskey(UserBasicInfoResponse profile, String name) {
        assertEquals(1, profile.getPasskeys().size());
        assertEquals(name, profile.getPasskeys().get(0).getName());
        assertEquals(List.of("internal"), profile.getPasskeys().get(0).getTransports());
    }
}
