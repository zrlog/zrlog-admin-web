package com.zrlog.admin.business.service;

import com.hibegin.common.util.PasswordHashUtils;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.exception.OldPasswordException;
import com.zrlog.admin.business.exception.UserNameAndPasswordRequiredException;
import com.zrlog.admin.business.exception.UserNameOrPasswordException;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.request.UpdatePasswordRequest;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UserServiceDatabaseTest {

    @Test
    public void shouldLoginAgainstUserTableAndUpgradeLegacyPasswordHash() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String submittedPassword = SecurityUtils.md5("secret");
            db.execute("update user set password=?, secretKey=? where userId=?", submittedPassword, "secret-key", 1);
            LoginRequest request = new LoginRequest();
            request.setUserName("admin");
            request.setPassword(submittedPassword);

            UserLoginDTO login = new UserService().login(request);
            String upgradedPassword = String.valueOf(db.scalar("select password from user where userId=?", 1));

            assertEquals(Integer.valueOf(1), login.getId());
            assertEquals("secret-key", login.getSecretKey());
            assertEquals("admin@example.com", login.getUserBasicInfoResponse().getEmail());
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

            UpdateRecordResponse response = new UserService().updatePassword(1, request);
            String updatedPassword = String.valueOf(db.scalar("select password from user where userId=?", 1));

            assertEquals(0, response.getError());
            assertTrue(PasswordHashUtils.matches(SecurityUtils.md5("new-password"), updatedPassword));
        }
    }

    @Test
    public void shouldLoadUserInfoWithCacheAndMfaStateThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("update user set mfaEnabled=?, mfaSecret=? where userId=?", 1, "JBSWY3DPEHPK3PXP", 1);

            UserInfoResponse response = new UserService().getUserInfoWithCache(1, "session-key");

            assertEquals("admin", response.getUserName());
            assertEquals("/avatar.png", response.getHeader());
            assertEquals("session-key", response.getKey());
            assertEquals(Boolean.FALSE, response.getLastVersion().getUpgrade());
            assertTrue(response.isMfaEnabled());
        }
    }

    @Test
    public void shouldRejectInvalidLoginAndPasswordUpdatesThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("update user set password=? where userId=?", SecurityUtils.md5("secret"), 1);
            UserService service = new UserService();

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
            UserService service = new UserService();
            UserBasicInfoResponse before = service.getBasicUserInfo(1, "session-key");
            UpdateAdminRequest request = new UpdateAdminRequest();
            request.setUserName("root");
            request.setEmail("root@example.com");
            request.setHeader("/attached/root.png");

            Object updated = service.update(1, request, null);
            Map<String, Object> row = db.queryOne("select userName,email,header from user where userId=?", 1);

            assertEquals("admin", before.getUserName());
            assertEquals("admin@example.com", before.getEmail());
            assertEquals("session-key", before.getKey());
            assertNotNull(before.getCacheableApiUris());
            assertTrue(updated instanceof Map);
            assertEquals("root", row.get("userName"));
            assertEquals("root@example.com", row.get("email"));
            assertEquals("/attached/root.png", row.get("header"));
        }
    }

    @Test
    public void shouldRejectMalformedInlineHeaderBeforeUpdatingUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            UserService service = new UserService();
            UpdateAdminRequest missingComma = profileRequest("data:image/png;base64");
            UpdateAdminRequest missingBase64Marker = profileRequest("data:image/png,abc");

            assertThrows(ArgsException.class, () -> service.update(1, missingComma, null));
            assertThrows(ArgsException.class, () -> service.update(1, missingBase64Marker, null));
            assertEquals("/avatar.png", db.queryOne("select header from user where userId=?", 1).get("header"));
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
