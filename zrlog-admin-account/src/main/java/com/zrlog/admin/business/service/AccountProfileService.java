package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;

/**
 * Assembles account details from user data and registered passkeys.
 */
@Singleton
public class AccountProfileService {
    private final UserService users;
    private final PasskeyService passkeys;

    @Inject
    public AccountProfileService(UserService users, PasskeyService passkeys) {
        this.users = users;
        this.passkeys = passkeys;
    }

    public UserBasicInfoResponse getBasicUserInfo(int userId, String sessionId) throws SQLException {
        UserBasicInfoResponse profile = users.getBasicUserInfo(userId, sessionId);
        profile.setPasskeys(passkeys.list(userId));
        return profile;
    }

    public UserBasicInfoResponse update(
            int userId,
            String sessionId,
            UpdateAdminRequest input,
            HttpRequest request,
            AvatarStorage storage) throws SQLException {
        users.update(userId, input, request, storage);
        return getBasicUserInfo(userId, sessionId);
    }
}
