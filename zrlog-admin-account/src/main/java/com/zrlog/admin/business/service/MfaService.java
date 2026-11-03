package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.exception.InvalidMfaCodeException;
import com.zrlog.admin.business.exception.MfaCodeRequiredException;
import com.zrlog.admin.business.rest.request.UpdateMfaRequest;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.util.MfaUtils;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import com.zrlog.model.User;
import com.zrlog.admin.business.exception.PermissionErrorException;
import java.util.Objects;

@Singleton
public class MfaService {
    private final jakarta.inject.Provider<UserRepository> userProvider;
    @Inject
    public MfaService(jakarta.inject.Provider<UserRepository> userProvider) {
        this.userProvider = userProvider;
    }

    public MfaStatusResponse getMfaStatus(int userId, String siteTitle) throws SQLException {
        User user = loadUser(userId);
        String userName = Objects.toString(user.getUserName(), "admin");
        boolean enabled = getMfaEnabled(user);
        String secret = getOrCreateMfaSecret(user);
        String issuer = Objects.toString(siteTitle, "ZrLog Admin");
        MfaStatusResponse response = new MfaStatusResponse();
        response.setEnabled(enabled);
        response.setSecret(secret);
        response.setIssuer(StringUtils.isEmpty(issuer) ? "ZrLog Admin" : issuer);
        response.setAccountName(userName);
        response.setOtpauthUrl(enabled || StringUtils.isEmpty(secret)
                ? ""
                : MfaUtils.buildOtpAuthUrl(response.getIssuer(), response.getAccountName(), secret));
        return response;
    }

    public UpdateRecordResponse enableMfa(int userId, UpdateMfaRequest request) throws SQLException {
        User user = loadUser(userId);
        String secret = getOrCreateMfaSecret(user);
        if (!MfaUtils.verifyCode(secret, request.getCode())) {
            throw new InvalidMfaCodeException();
        }
        updateMfaFields(user, true, secret);
        UpdateRecordResponse response = new UpdateRecordResponse(true);
        response.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.mfa.enable.success"));
        return response;
    }

    public UpdateRecordResponse disableMfa(int userId, UpdateMfaRequest request) throws SQLException {
        User user = loadUser(userId);
        if (!getMfaEnabled(user)) {
            UpdateRecordResponse response = new UpdateRecordResponse(true);
            response.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.mfa.disable.success"));
            return response;
        }
        String secret = getMfaSecret(user);
        if (StringUtils.isEmpty(secret) || !MfaUtils.verifyCode(secret, request.getCode())) {
            throw new InvalidMfaCodeException();
        }
        updateMfaFields(user, false, null);
        UpdateRecordResponse response = new UpdateRecordResponse(true);
        response.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.mfa.disable.success"));
        return response;
    }

    public void verifyLoginMfa(User user, String mfaCode) {
        if (!getMfaEnabled(user)) {
            return;
        }
        if (StringUtils.isEmpty(mfaCode)) {
            throw new MfaCodeRequiredException();
        }
        String secret = getMfaSecret(user);
        if (StringUtils.isEmpty(secret) || !MfaUtils.verifyCode(secret, mfaCode)) {
            throw new InvalidMfaCodeException();
        }
    }

    public boolean isMfaEnabled(int userId) throws SQLException {
        return getMfaEnabled(loadUser(userId));
    }

    public boolean getMfaEnabled(User user) {
        return user != null && Boolean.TRUE.equals(user.getMfaEnabled());
    }

    private String getMfaSecret(User user) {
        return Objects.toString(user.getMfaSecret(), "");
    }

    private User loadUser(int userId) throws SQLException {
        User user = userProvider.get().loadById((long) userId);
        if (user == null) throw new PermissionErrorException();
        return user;
    }

    private String getOrCreateMfaSecret(User user) throws SQLException {
        String secret = getMfaSecret(user);
        if (StringUtils.isNotEmpty(secret)) return secret;
        secret = MfaUtils.generateSecret();
        user.setMfaSecret(secret);
        userProvider.get().update(user);
        return secret;
    }

    private void updateMfaFields(User user, boolean enabled, String secret) throws SQLException {
        user.setMfaEnabled(enabled);
        user.setMfaSecret(secret);
        userProvider.get().update(user);
    }
}
