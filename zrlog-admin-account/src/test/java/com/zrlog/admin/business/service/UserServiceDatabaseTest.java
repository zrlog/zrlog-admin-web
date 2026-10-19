package com.zrlog.admin.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.exception.OldPasswordException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UserNameAndPasswordRequiredException;
import com.zrlog.admin.business.exception.UserNameOrPasswordException;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.request.UpdatePasswordRequest;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.model.User;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;

public class UserServiceDatabaseTest {


    @Test
    public void accountCacheMetadataComesFromInjectedResourcesWithoutGlobals() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            var uris = new HashSet<String>(java.util.Set.of("/api/admin/site-a"));
            var users =
                    new UserService(
                            () -> uris,
                            AccountServicesFixture.component().mfa(),
                            AccountServicesFixture.component().preferences(),
                            User::new,
                            () -> AccountServicesFixture.component().passkeys());
            var first = users.getBasicUserInfo(1, "first");
            assertEquals(java.util.Set.of("/api/admin/site-a"), first.getCacheableApiUris());
            uris.clear();
            uris.add("/api/admin/site-b");
            assertEquals(java.util.Set.of("/api/admin/site-b"), users.getBasicUserInfo(1, "second").getCacheableApiUris());
            assertEquals(java.util.Set.of("/api/admin/site-a"), first.getCacheableApiUris());
        }
    }

    @Test
    public void shouldLoginAgainstUserTableAndUpgradeLegacyPasswordHash() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String submittedPassword = SecurityUtils.md5("secret");
            db.execute("update user set password=?, secretKey=? where userId=?", submittedPassword, "secret-key", 1);
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\",\"appearance\":{\"theme\":\"desk\"}}");
            LoginRequest request = new LoginRequest();
            request.setUserName("admin");
            request.setPassword(submittedPassword);

            UserLoginDTO login =
                    new UserService(
                                    java.util.Collections::emptySet,
                                    AccountServicesFixture.component().mfa(),
                                    AccountServicesFixture.component().preferences(),
                                    User::new,
                                    () -> AccountServicesFixture.component().passkeys())
                            .login(request);
            String upgradedPassword = String.valueOf(db.scalar("select password from user where userId=?", 1));

            assertEquals(Integer.valueOf(1), login.getId());
            assertEquals("secret-key", login.getSecretKey());
            assertEquals("admin@example.com", login.getUserBasicInfoResponse().getEmail());
            assertEquals("en_US", login.getUserBasicInfoResponse().getLanguage());
            assertEquals("desk", login.getUserBasicInfoResponse().getAppearance().theme);
            assertFalse(PasswordHashUtils.isLegacyMd5(upgradedPassword));
            assertTrue(PasswordHashUtils.matches(submittedPassword, upgradedPassword));
        }
    }

    @Test
    public void shouldUpdatePasswordUsingRealUserDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String oldNormalized = SecurityUtils.md5("old-password");
            db.execute("update user set password=? where userId=?", PasswordHashUtils.hash(oldNormalized), 1);
            UpdatePasswordRequest request = new UpdatePasswordRequest();
            request.setOldPassword("old-password");
            request.setNewPassword("new-password");

            UpdateRecordResponse response =
                    new UserService(
                                    java.util.Collections::emptySet,
                                    AccountServicesFixture.component().mfa(),
                                    AccountServicesFixture.component().preferences(),
                                    User::new,
                                    () -> AccountServicesFixture.component().passkeys())
                            .updatePassword(1, request);
            String updatedPassword = String.valueOf(db.scalar("select password from user where userId=?", 1));

            assertEquals(0, response.getError());
            assertTrue(PasswordHashUtils.matches(SecurityUtils.md5("new-password"), updatedPassword));
        }
    }

    @Test
    public void shouldLoadUserInfoWithCacheAndMfaStateThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("update user set mfaEnabled=?, mfaSecret=? where userId=?", 1, "JBSWY3DPEHPK3PXP", 1);

            UserInfoResponse response =
                    new UserService(
                                    java.util.Collections::emptySet,
                                    AccountServicesFixture.component().mfa(),
                                    AccountServicesFixture.component().preferences(),
                                    User::new,
                                    () -> AccountServicesFixture.component().passkeys())
                            .getUserInfoWithCache(1, "session-key", db.cacheService());

            assertEquals("admin", response.getUserName());
            assertEquals("/avatar.png", response.getHeader());
            assertEquals("session-key", response.getKey());
            assertEquals(Boolean.FALSE, response.getLastVersion().getUpgrade());
            assertTrue(response.isMfaEnabled());
        }
    }

    @Test
    public void shouldResolveAccountAppearanceWithoutFreezingDefaultsInTheIdentityCache() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.putWebsite("admin_theme", "antd");
            db.putWebsite("admin_darkMode", true);
            db.execute("update user set preferences=? where userId=1",
                    "{\"language\":\"en_US\",\"appearance\":{\"darkMode\":false}}");
            UserService service =
                    new UserService(
                            java.util.Collections::emptySet,
                            AccountServicesFixture.component().mfa(),
                            AccountServicesFixture.component().preferences(),
                            User::new,
                            () -> AccountServicesFixture.component().passkeys());
            UserInfoResponse owner = service.getUserInfoWithCache(1, "owner-session", db.cacheService());
            UserInfoResponse writer = service.getUserInfoWithCache(2, "writer-session", db.cacheService());
            assertEquals("en_US", owner.getLanguage());
            assertEquals("zh_CN", writer.getLanguage());
            assertEquals("antd", owner.getAppearance().theme);
            assertFalse(owner.getAppearance().darkMode);
            assertTrue(writer.getAppearance().darkMode);
            assertEquals("writer-session", writer.getKey());
            assertEquals("author", writer.getRole());
            assertEquals("en_US", service.getBasicUserInfo(1, "owner-session").getLanguage());
            // Passkey login uses this same builder without a thread-local browser identity.
            assertEquals(
                    "zh_CN",
                    service.buildLoginDTO(new User().loadById(2))
                            .getUserBasicInfoResponse()
                            .getLanguage());

            db.putWebsite("admin_theme", "desk");
            assertEquals("desk", service.getUserInfoWithCache(1, "owner-session", db.cacheService()).getAppearance().theme);
            new UserPreferenceService(
                            ApplicationServicesFixture.settings(),
                            new UserPreferenceStore(),
                            AccountServicesFixture.component().permissions())
                    .updateBody("{}", "appearance");
            UserInfoResponse reset = service.getUserInfoWithCache(1, "owner-session", db.cacheService());
            assertEquals("zh_CN", reset.getLanguage());
            assertTrue(reset.getAppearance().darkMode);
            assertEquals("desk", reset.getAppearance().theme);
            com.google.gson.JsonObject json = new com.google.gson.Gson().toJsonTree(reset).getAsJsonObject();
            assertEquals("desk", json.getAsJsonObject("appearance").get("theme").getAsString());
            assertFalse(json.has("defaults"));
            assertFalse(json.has("editor"));
        }
    }

    @Test
    public void shouldRejectInvalidLoginAndPasswordUpdatesThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("update user set password=? where userId=?", SecurityUtils.md5("secret"), 1);
            UserService service =
                    new UserService(
                            java.util.Collections::emptySet,
                            AccountServicesFixture.component().mfa(),
                            AccountServicesFixture.component().preferences(),
                            User::new,
                            () -> AccountServicesFixture.component().passkeys());

            assertThrows(UserNameAndPasswordRequiredException.class, () -> service.login(loginRequest("", "")));
            assertThrows(UserNameOrPasswordException.class, () -> service.login(loginRequest("missing", "secret")));
            assertThrows(UserNameOrPasswordException.class, () -> service.login(loginRequest("admin", "bad")));
            assertEquals(1, service.updatePassword(1, null).getError());

            UpdatePasswordRequest missingNewPassword = new UpdatePasswordRequest();
            missingNewPassword.setOldPassword("old");
            assertThrows(ArgsException.class, () -> service.updatePassword(1, missingNewPassword));

            UpdatePasswordRequest wrongOldPassword = new UpdatePasswordRequest();
            wrongOldPassword.setOldPassword("wrong");
            wrongOldPassword.setNewPassword("new");
            assertThrows(OldPasswordException.class, () -> service.updatePassword(1, wrongOldPassword));
        }
    }

    @Test
    public void shouldLoadAndUpdateUserProfileThroughRealUserDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            UserService service =
                    new UserService(
                            java.util.Collections::emptySet,
                            AccountServicesFixture.component().mfa(),
                            AccountServicesFixture.component().preferences(),
                            User::new,
                            () -> AccountServicesFixture.component().passkeys());
            UserBasicInfoResponse before = service.getBasicUserInfo(1, "session-key");
            UpdateAdminRequest request = new UpdateAdminRequest();
            request.setUserName("root");
            request.setEmail("root@example.com");
            request.setHeader("/attached/root.png");

            Object updated = service.update(1, request, null, null);
            Map<String, Object> row = db.queryOne("select userName,email,header from user where userId=?", 1);

            assertEquals("admin", before.getUserName());
            assertEquals("admin@example.com", before.getEmail());
            assertEquals("session-key", before.getKey());
            assertNotNull(before.getCacheableApiUris());
            assertTrue(updated instanceof UserBasicInfoResponse);
            assertFalse(new com.google.gson.Gson().toJson(updated).contains("password"));
            assertFalse(new com.google.gson.Gson().toJson(updated).contains("secretKey"));
            assertEquals("root", row.get("userName"));
            assertEquals("root@example.com", row.get("email"));
            assertEquals("/attached/root.png", row.get("header"));
        }
    }

    @Test
    public void shouldRejectMalformedInlineHeaderBeforeUpdatingUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            UserService service =
                    new UserService(
                            java.util.Collections::emptySet,
                            AccountServicesFixture.component().mfa(),
                            AccountServicesFixture.component().preferences(),
                            User::new,
                            () -> AccountServicesFixture.component().passkeys());
            UpdateAdminRequest missingComma = profileRequest("data:image/png;base64");
            UpdateAdminRequest missingBase64Marker = profileRequest("data:image/png,abc");

            assertThrows(ArgsException.class, () -> service.update(1, missingComma, null, null));
            assertThrows(ArgsException.class, () -> service.update(1, missingBase64Marker, null, null));
            assertEquals("/avatar.png", db.queryOne("select header from user where userId=?", 1).get("header"));
        }
    }

    @Test
    public void profileUsesTheSuppliedAvatarStorageAndRejectsMissingStorageWithoutChangingTheUser() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            {
                UserService service =
                        new UserService(
                                java.util.Collections::emptySet,
                                AccountServicesFixture.component().mfa(),
                                AccountServicesFixture.component().preferences(),
                                User::new,
                                () -> AccountServicesFixture.component().passkeys());
                UpdateAdminRequest input = profileRequest("data:image/webp;base64,AQID");
                assertThrows(
                        PermissionErrorException.class, () -> service.update(1, input, null, null));
                assertEquals("/avatar.png", db.scalar("select header from user where userId=1"));
                var storage =
                        new AvatarStorage() {
                            @Override
                            public void setup() {}

                            @Override
                            public String saveAvatar(
                                    byte[] bytes,
                                    String extension,
                                    com.hibegin.http.server.api.HttpRequest request) {
                                org.junit.Assert.assertArrayEquals(new byte[] {1, 2, 3}, bytes);
                                assertEquals("webp", extension);
                                return "https://assets.example/avatars/profile.webp";
                            }
                        };
                assertEquals("https://assets.example/avatars/profile.webp", service.update(1, input, null, storage).getHeader());
                assertEquals("https://assets.example/avatars/profile.webp", db.scalar("select header from user where userId=1"));
            }
        }
    }

    private static LoginRequest loginRequest(String userName, String password) {
        LoginRequest request = new LoginRequest();
        request.setUserName(userName);
        request.setPassword(password);
        return request;
    }

    private static UpdateAdminRequest profileRequest(String header) {
        UpdateAdminRequest request = new UpdateAdminRequest();
        request.setUserName("root");
        request.setEmail("root@example.com");
        request.setHeader(header);
        return request;
    }
}
