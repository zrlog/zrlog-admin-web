package com.zrlog.admin.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.InvalidMfaCodeException;
import com.zrlog.admin.business.exception.MfaCodeRequiredException;
import com.zrlog.admin.business.rest.request.UpdateMfaRequest;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.util.MfaUtils;
import com.zrlog.util.I18nUtil;
import com.zrlog.model.User;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

public class MfaService {

    private static final String MFA_ENABLED_COLUMN = "mfaEnabled";
    private static final String MFA_SECRET_COLUMN = "mfaSecret";

    public MfaStatusResponse getMfaStatus(int userId) throws SQLException {
        Map<String, Object> user = new User().loadById(userId);
        String userName = Objects.toString(user.get("userName"), "admin");
        boolean enabled = getMfaEnabled(user);
        String secret = enabled ? "" : getOrCreateMfaSecret(userId);
        String issuer = Objects.toString(AdminConstants.getPublicWebSiteInfo().getTitle(), "ZrLog Admin");
        MfaStatusResponse response = new MfaStatusResponse();
        response.setEnabled(enabled);
        response.setSecret(secret);
        response.setIssuer(StringUtils.isEmpty(issuer) ? "ZrLog Admin" : issuer);
        response.setAccountName(userName);
        response.setOtpauthUrl(StringUtils.isEmpty(secret)
                ? ""
                : MfaUtils.buildOtpAuthUrl(response.getIssuer(), response.getAccountName(), secret));
        return response;
    }

    public UpdateRecordResponse enableMfa(int userId, UpdateMfaRequest request) throws SQLException {
        String secret = getOrCreateMfaSecret(userId);
        if (!MfaUtils.verifyCode(secret, request.getCode())) {
            throw new InvalidMfaCodeException();
        }
        updateMfaFields(userId, true, secret);
        UpdateRecordResponse response = new UpdateRecordResponse(true);
        response.setMessage(I18nUtil.getAdminBackendStringFromRes("mfaEnableSuccess"));
        return response;
    }

    public UpdateRecordResponse disableMfa(int userId, UpdateMfaRequest request) throws SQLException {
        Map<String, Object> user = new User().loadById(userId);
        if (!getMfaEnabled(user)) {
            UpdateRecordResponse response = new UpdateRecordResponse(true);
            response.setMessage(I18nUtil.getAdminBackendStringFromRes("mfaDisableSuccess"));
            return response;
        }
        String secret = getMfaSecret(user);
        if (StringUtils.isEmpty(secret) || !MfaUtils.verifyCode(secret, request.getCode())) {
            throw new InvalidMfaCodeException();
        }
        updateMfaFields(userId, false, null);
        UpdateRecordResponse response = new UpdateRecordResponse(true);
        response.setMessage(I18nUtil.getAdminBackendStringFromRes("mfaDisableSuccess"));
        return response;
    }

    public void verifyLoginMfa(Map<String, Object> user, String mfaCode) {
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
        return getMfaEnabled(new User().loadById(userId));
    }

    public boolean getMfaEnabled(Map<String, Object> user) {
        Object value = user.get(MFA_ENABLED_COLUMN);
        if (Objects.isNull(value)) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString());
    }

    private String getMfaSecret(Map<String, Object> user) {
        return Objects.toString(user.get(MFA_SECRET_COLUMN), "");
    }

    private String getOrCreateMfaSecret(int userId) throws SQLException {
        Map<String, Object> user = new User().loadById(userId);
        String secret = getMfaSecret(user);
        if (StringUtils.isNotEmpty(secret)) {
            return secret;
        }
        secret = MfaUtils.generateSecret();
        new User().execute("update user set " + MFA_SECRET_COLUMN + "=? where userId=?", secret, userId);
        return secret;
    }

    private void updateMfaFields(int userId, boolean enabled, String secret) throws SQLException {
        new User().execute("update user set " + MFA_ENABLED_COLUMN + "=?, " + MFA_SECRET_COLUMN + "=? where userId=?",
                enabled ? 1 : 0, secret, userId);
    }
}
