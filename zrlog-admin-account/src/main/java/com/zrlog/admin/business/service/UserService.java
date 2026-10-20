package com.zrlog.admin.business.service;

import com.hibegin.common.util.*;
import com.hibegin.http.server.api.HttpRequest;
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
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

@Singleton
public class UserService {
    private final UserPreferenceService userPreferenceService;
    private final jakarta.inject.Provider<User> userProvider;

    private final Supplier<Set<String>> cacheableApiUris;
    private final MfaService mfaService;

    @Inject
    public UserService(
            Supplier<Set<String>> cacheableApiUris,
            MfaService mfaService,
            UserPreferenceService userPreferenceService,
            jakarta.inject.Provider<User> userProvider) {
        this.mfaService = mfaService;
        this.userPreferenceService = userPreferenceService;
        this.userProvider = userProvider;

        this.cacheableApiUris = Objects.requireNonNull(cacheableApiUris);
    }

    public UpdateRecordResponse updatePassword(int currentUserId, UpdatePasswordRequest updatePasswordRequest) throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        if (Objects.isNull(updatePasswordRequest)) {
            return new UpdateRecordResponse(false);
        }
        if (StringUtils.isNotEmpty(updatePasswordRequest.getOldPassword()) && StringUtils.isNotEmpty(updatePasswordRequest.getNewPassword())) {
            User user = this.userProvider.get();
            String dbPassword = user.getPasswordByUserId(currentUserId);
            String oldPassword = normalizeSubmittedPassword(updatePasswordRequest.getOldPassword());
            if (verifyPassword(oldPassword, dbPassword)) {
                user.updatePassword(currentUserId, encodePassword(normalizeSubmittedPassword(updatePasswordRequest.getNewPassword())));
                user.execute("update user set authVersion=authVersion+1 where userId=?", currentUserId);
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
        Map<String, Object> byId = this.userProvider.get().loadById(userId);
        UserBasicInfoResponse userInfoByUser = getUserInfoByUser(BeanUtil.convert(byId, UserBasicDTO.class), sessionId);
        userInfoByUser.setEmail(ObjectHelpers.requireNonNullElse((String) byId.get("email"), ""));
        userInfoByUser.setMfaEnabled(mfaService.getMfaEnabled(byId));
        return userInfoByUser;
    }

    public UserInfoResponse getUserInfoWithCache(int userId, String sessionId, CacheService cacheService) throws SQLException {
        UserBasicDTO userInfoById = cacheService.getUserInfoById((long) userId);
        boolean mfaEnabled = mfaService.isMfaEnabled(userId);
        UserInfoResponse result = new UserInfoResponse(userInfoById.getUserName(),
                StringUtils.isEmpty(userInfoById.getHeader()) ? getDefaultHeaderImage() : userInfoById.getHeader(),
                sessionId, buildLastVersionPlaceholder(), mfaEnabled);
        result.setUserId(userId);
        result.setRole(AccountAccess.from(userProvider.get().loadById(userId)).getRole());
        fillDisplayPreferences(result, userId);
        return result;
    }

    private void fillDisplayPreferences(UserInfoResponse response, int userId) throws SQLException {
        // Resolve inherited defaults on each account response; the identity cache must not freeze
        // them.
        var preferences = this.userPreferenceService.effective(userId);
        response.setLanguage(preferences.language);
        response.setAppearance(preferences.appearance);
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
        try {
            basicInfoResponse.setUserId(userBasicDTO.getUserId().intValue());
            basicInfoResponse.setRole(
                    AccountAccess.from(
                                    userProvider
                                            .get()
                                            .loadById(userBasicDTO.getUserId().intValue()))
                            .getRole());
            fillDisplayPreferences(basicInfoResponse, userBasicDTO.getUserId().intValue());
        } catch (SQLException e) {
            throw new PermissionErrorException();
        }
        basicInfoResponse.setCacheableApiUris(EnvKit.isFaaSMode()
                ? new HashSet<>() : new HashSet<>(cacheableApiUris.get()));
        return basicInfoResponse;
    }

    public UserLoginDTO login(LoginRequest loginRequest) throws SQLException {
        if (StringUtils.isEmpty(loginRequest.getUserName()) || StringUtils.isEmpty(loginRequest.getPassword())) {
            throw new UserNameAndPasswordRequiredException();
        }
        User userDao = this.userProvider.get();
        Map<String, Object> user = userDao.getUserByUserName(loginRequest.getUserName().toLowerCase());
        if (Objects.isNull(user) || !AccountAccess.from(user).isEnabled()) {
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
        Map<String, Object> user = this.userProvider.get().loadById(userId);
        if (Objects.isNull(user) || !verifyPassword(submittedPassword, Objects.toString(user.get("password"), ""))) {
            throw new OldPasswordException();
        }
        mfaService.verifyLoginMfa(user, mfaCode);
    }

    public UserLoginDTO buildLoginDTO(Map<String, Object> user) {
        if (!AccountAccess.from(user).isEnabled()) throw new UserNameOrPasswordException();
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

    public void update(int userId, UpdateAdminRequest updateAdminRequest,
                       HttpRequest request, AvatarStorage storage) throws SQLException {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
        this.userProvider
                .get()
                .updateEmailUserNameHeaderByUserId(
                        updateAdminRequest.getEmail(),
                        updateAdminRequest.getUserName(),
                        normalizeHeader(updateAdminRequest.getHeader(), request, storage),
                        userId);
    }

    private String normalizeHeader(String header, HttpRequest request, AvatarStorage storage) {
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
        if (storage == null) throw new PermissionErrorException();
        return storage.saveAvatar(bytes, extension, request);
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
