package com.zrlog.admin.data.repository;

import static org.junit.Assert.*;

import com.hibegin.common.dao.entity.OptimisticLockException;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AdminUserRepositoryDatabaseTest {
    private final String backend;

    public AdminUserRepositoryDatabaseTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() {
        return new Object[] {"H2", "SQLITE", "WEB_API"};
    }

    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API")
                ? ZrLogTestDatabase.openWebApi()
                : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend));
    }

    @Test
    public void updatesCredentialsAndProfileWithoutChangingAuthorityOrOverwritingStaleRows()
            throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.update(
                    "INSERT INTO user(userId,email,password,userName,header,role,authVersion,preferences) VALUES(?,?,?,?,?,?,?,?)",
                    1,
                    "admin@example.com",
                    "password",
                    "admin",
                    "/avatar.png",
                    "author",
                    7,
                    "{}");
            AdminUserRepository users = new AdminUserRepository(db.dataSource());
            var stale = users.getUserByUserName("admin");
            assertEquals(Long.valueOf(1), stale.getId());
            assertEquals("password", users.getPasswordByUserId(1));

            assertTrue(users.updatePassword(1, "new-password"));
            users.updateEmailUserNameHeaderByUserId("new@example.com", "new-admin", "/new.png", 1);

            assertNull(users.getUserByUserName("admin"));
            var saved = users.getUserByUserName("new-admin");
            assertEquals("new-password", users.getPasswordByUserId(1));
            assertEquals("new@example.com", saved.getEmail());
            assertEquals("/new.png", saved.getHeader());
            assertEquals("author", saved.getRole());
            assertEquals(Integer.valueOf(7), saved.getAuthVersion());
            assertEquals("{}", saved.getPreferences());
            assertNull(saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());
            assertEquals(Long.valueOf(2), saved.getVersion());
            stale.setEmail("stale@example.com");
            assertThrows(OptimisticLockException.class, () -> users.update(stale));
            assertEquals("new@example.com", users.loadById(1L).getEmail());
        }
    }

    @Test
    public void missingAccountsRemainMissingAfterProfileAndPasswordUpdates() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            AdminUserRepository users = new AdminUserRepository(db.dataSource());
            assertNull(users.getUserByUserName("missing"));
            assertNull(users.getPasswordByUserId(1));
            assertFalse(users.updatePassword(1, "password"));
            users.updateEmailUserNameHeaderByUserId(
                    "missing@example.com", "missing", "/avatar.png", 1);
            assertNull(users.loadById(1L));
        }
    }
}
