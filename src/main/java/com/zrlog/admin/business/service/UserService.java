package com.zrlog.admin.business.service;

import com.hibegin.common.util.*;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
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
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.User;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.sql.SQLException;
import java.util.*;

public class UserService {

    private final CacheService cacheService;
    private final MfaService mfaService;

    public UserService() {
        this.cacheService = Constants.zrLogConfig.getCacheService();
        this.mfaService = new MfaService();
    }

    public UpdateRecordResponse updatePassword(int currentUserId, UpdatePasswordRequest updatePasswordRequest) throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        if (Objects.isNull(updatePasswordRequest)) {
            return new UpdateRecordResponse(false);
        }
        if (StringUtils.isNotEmpty(updatePasswordRequest.getOldPassword()) && StringUtils.isNotEmpty(updatePasswordRequest.getNewPassword())) {
            User user = new User();
            String dbPassword = user.getPasswordByUserId(currentUserId);
            String oldPassword = normalizeSubmittedPassword(updatePasswordRequest.getOldPassword());
            if (verifyPassword(oldPassword, dbPassword)) {
                user.updatePassword(currentUserId, encodePassword(normalizeSubmittedPassword(updatePasswordRequest.getNewPassword())));
                UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse();
                updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.user.password.change.success"));
                return updateRecordResponse;
            } else {
                throw new OldPasswordException();
            }
        } else {
            throw new ArgsException();
        }
    }

    public UserBasicInfoResponse getBasicUserInfo(int userId, String sessionId) throws SQLException {
        Map<String, Object> byId = new User().loadById(userId);
        UserBasicInfoResponse userInfoByUser = getUserInfoByUser(BeanUtil.convert(byId, UserBasicDTO.class), sessionId);
        userInfoByUser.setEmail(ObjectHelpers.requireNonNullElse((String) byId.get("email"), ""));
        userInfoByUser.setMfaEnabled(mfaService.getMfaEnabled(byId));
        return userInfoByUser;
    }

    public UserInfoResponse getUserInfoWithCache(int userId, String sessionId) throws SQLException {
        UserBasicDTO userInfoById = cacheService.getUserInfoById((long) userId);
        boolean mfaEnabled = mfaService.isMfaEnabled(userId);
        if (StringUtils.isEmpty(userInfoById.getHeader())) {
            return new UserInfoResponse(userInfoById.getUserName(), getDefaultHeaderImage(), sessionId, buildLastVersionPlaceholder(), mfaEnabled);
        }
        return new UserInfoResponse(userInfoById.getUserName(), userInfoById.getHeader(), sessionId, buildLastVersionPlaceholder(), mfaEnabled);
    }

    private String getDefaultHeaderImage() {
        byte[] byteByInputStream = IOUtil.getByteByInputStream(UserService.class.getResourceAsStream("/assets/admin/images/default-portrait.gif"));
        return "data:image/gif;base64," + Base64.getEncoder().encodeToString(byteByInputStream);
    }

    private UserBasicInfoResponse getUserInfoByUser(UserBasicDTO userBasicDTO, String sessionId) {
        UserBasicInfoResponse basicInfoResponse = ObjectUtil.requireNonNullElse(BeanUtil.convert(userBasicDTO, UserBasicInfoResponse.class), new UserBasicInfoResponse());
        if (StringUtils.isEmpty(basicInfoResponse.getHeader())) {
            basicInfoResponse.setHeader(getDefaultHeaderImage());
        }
        basicInfoResponse.setLastVersion(buildLastVersionPlaceholder());
        basicInfoResponse.setKey(sessionId);
        if (EnvKit.isFaaSMode()) {
            basicInfoResponse.setCacheableApiUris(new HashSet<>());
        } else {
            basicInfoResponse.setCacheableApiUris(AdminConstants.adminResource.getAdminCacheableApiUris());
        }
        return basicInfoResponse;
    }

    public UserLoginDTO login(LoginRequest loginRequest) throws SQLException {
        if (StringUtils.isEmpty(loginRequest.getUserName()) || StringUtils.isEmpty(loginRequest.getPassword())) {
            throw new UserNameAndPasswordRequiredException();
        }
        User userDao = new User();
        Map<String, Object> user = userDao.getUserByUserName(loginRequest.getUserName().toLowerCase());
        if (Objects.isNull(user)) {
            throw new UserNameOrPasswordException();
        }
        String dbPassword = (String) user.get("password");
        String submittedPassword = loginRequest.getPassword();
        if (!verifyPassword(submittedPassword, dbPassword)) {
            throw new UserNameOrPasswordException();
        }
        int userId = ((Number) user.get("userId")).intValue();
        upgradePasswordIfNeeded(userDao, userId, dbPassword, submittedPassword);
        mfaService.verifyLoginMfa(user, loginRequest.getMfaCode());
        return buildLoginDTO(user);
    }

    public void verifyCurrentCredentials(int userId, String submittedPassword, String mfaCode) throws SQLException {
        Map<String, Object> user = new User().loadById(userId);
        if (Objects.isNull(user) || !verifyPassword(submittedPassword, Objects.toString(user.get("password"), ""))) {
            throw new OldPasswordException();
        }
        mfaService.verifyLoginMfa(user, mfaCode);
    }

    public UserLoginDTO buildLoginDTO(Map<String, Object> user) {
        int userId = ((Number) user.get("userId")).intValue();
        UserBasicDTO basicDTO = BeanUtil.convert(user, UserBasicDTO.class);
        UserBasicInfoResponse userInfoByUser = getUserInfoByUser(basicDTO, UUID.randomUUID().toString());
        userInfoByUser.setEmail(ObjectHelpers.requireNonNullElse((String) user.get("email"), ""));
        userInfoByUser.setMfaEnabled(mfaService.getMfaEnabled(user));
        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setSecretKey((String) user.get("secretKey"));
        userLoginDTO.setUserBasicInfoResponse(userInfoByUser);
        userLoginDTO.setId(userId);
        return userLoginDTO;
    }


    public Object update(int userId, UpdateAdminRequest updateAdminRequest, HttpRequest request) throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        new User().updateEmailUserNameHeaderByUserId(updateAdminRequest.getEmail(), updateAdminRequest.getUserName(),
                normalizeHeader(updateAdminRequest.getHeader(), request), userId);
        return new User().loadById(userId);
    }

    private String normalizeHeader(String header, HttpRequest request) {
        if (StringUtils.isEmpty(header) || !header.startsWith("data:image/")) {
            return header;
        }
        int commaIndex = header.indexOf(',');
        if (commaIndex <= 0) {
            throw new ArgsException("header");
        }
        String meta = header.substring(5, commaIndex);
        if (!meta.contains(";base64")) {
            throw new ArgsException("header");
        }
        String mimeType = meta.split(";")[0];
        String extension = toImageExtension(mimeType);
        byte[] bytes = Base64.getDecoder().decode(header.substring(commaIndex + 1));
        return new UploadService().saveBytes(bytes, extension, "image", request, AdminTokenThreadLocal.getUser()).getUrl();
    }

    private String toImageExtension(String mimeType) {
        if (mimeType.contains("jpeg") || mimeType.contains("jpg")) {
            return "jpg";
        }
        if (mimeType.contains("webp")) {
            return "webp";
        }
        return "png";
    }

    private CheckVersionResponse buildLastVersionPlaceholder() {
        CheckVersionResponse checkVersionResponse = new CheckVersionResponse();
        checkVersionResponse.setUpgrade(false);
        return checkVersionResponse;
    }

    private String normalizeSubmittedPassword(String rawPassword) {
        return SecurityUtils.md5(rawPassword);
    }

    private String encodePassword(String normalizedPassword) {
        return PasswordHashUtils.hash(normalizedPassword);
    }

    private boolean verifyPassword(String normalizedPassword, String dbPassword) {
        if (StringUtils.isEmpty(normalizedPassword) || StringUtils.isEmpty(dbPassword)) {
            return false;
        }
        if (PasswordHashUtils.isLegacyMd5(dbPassword)) {
            return normalizedPassword.equalsIgnoreCase(dbPassword);
        }
        return PasswordHashUtils.matches(normalizedPassword, dbPassword);
    }

    private void upgradePasswordIfNeeded(User userDao, int userId, String dbPassword, String normalizedPassword) throws SQLException {
        if (PasswordHashUtils.needsRehash(dbPassword)) {
            userDao.updatePassword(userId, encodePassword(normalizedPassword));
        }
    }
}
