package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.IOUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.CacheService;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

public class AdminUserController extends BaseController {
    private final UserPreferenceService userPreferenceService;
    private final AdminRequestIdentity requestIdentity;
    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;


    private final UserService userService;
    private final AccountProfileService profiles;
    private final MfaService mfaService;
    private final PasskeyService passkeyService;

    private final CacheService cacheService;
    private final Supplier<AvatarStorage> avatarStorage;

    @Inject
    public AdminUserController(
            UserService userService,
            AccountProfileService profiles,
            CacheService cacheService,
            Supplier<AvatarStorage> avatarStorage,
            AdminPageDataFactory pages,
            PasskeyService passkeyService,
            MfaService mfaService,
            UserPreferenceService userPreferenceService,
            AdminAuditService adminAuditService,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.mfaService = mfaService;
        this.userPreferenceService = userPreferenceService;
        this.adminAuditService = adminAuditService;

        this.passkeyService = passkeyService;
        this.pages = pages;
        this.userService = userService;
        this.profiles = profiles;
        this.cacheService = cacheService;
        this.avatarStorage = Objects.requireNonNull(avatarStorage);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.profile")
    public AdminPageDataResponse<UserBasicInfoResponse> index() throws SQLException {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return pages.response(requestIdentity.optional(), profiles.getBasicUserInfo(adminTokenVO.getUserId(), adminTokenVO.getSessionId()), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.preferences")
    public AdminPageDataResponse<UserPreferencesResponse> preferences() throws SQLException {
        var actor = requestIdentity.current();
        return pages.response(actor, this.userPreferenceService.current(actor), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.updatePreferences")
    public ApiStandardResponse<UserPreferencesResponse> updatePreferences() throws SQLException {
        java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
        String contentType = request.getHeader("Content-Type");
        if (body == null || body.remaining() > 8192 || contentType == null
                || !contentType.split(";")[0].trim().equalsIgnoreCase("application/json")) {
            throw new ArgsException("preferences");
        }
        return new ApiStandardResponse<>(
                this.userPreferenceService.updateBody(
                        requestIdentity.current(),
                        IOUtil.getStringInputStream(request.getInputStream()),
                        request.getParaToStr("section")));
    }

    /**
     * 校验是否处于登录状态，不需要返回过多的用户信息，可以返回一些全局需要使用到的与用户相关的信息，比如 头像/昵称,新版本
     *
     * @return 基础的用户信息
     */
    @ResponseBody
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.identity")
    public AdminPageDataResponse<UserInfoResponse> info() throws SQLException {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return pages.response(requestIdentity.optional(), userService.getUserInfoWithCache(adminTokenVO.getUserId(), adminTokenVO.getSessionId(), cacheService), "", request.getUri());
    }

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.updateProfile")
    public UpdateRecordResponse update() throws SQLException {
        UpdateAdminRequest updateAdminRequest = getRequestBodyWithNullCheck(UpdateAdminRequest.class);
        AdminTokenVO token = AdminTokenThreadLocal.getUser();
        profiles.update(token.getUserId(), token.getSessionId(), updateAdminRequest, getRequest(), avatarStorage.get());
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.UPDATE_PROFILE);
        UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse(true);
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.user.update.success"));
        return updateRecordResponse;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.updatePassword")
    public UpdateRecordResponse updatePassword() throws SQLException {
        UpdateRecordResponse response = userService.updatePassword(AdminTokenThreadLocal.getUserId(),
                getRequestBodyWithNullCheck(UpdatePasswordRequest.class));
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.UPDATE_PASSWORD);
        return response;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.mfa")
    public AdminPageDataResponse<MfaStatusResponse> mfa() throws SQLException {
        return pages.response(requestIdentity.optional(), mfaService.getMfaStatus(AdminTokenThreadLocal.getUserId(), cacheService.getPublicWebSiteInfo().getTitle()), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.enableMfa")
    public UpdateRecordResponse enableMfa() throws SQLException {
        UpdateRecordResponse response = mfaService.enableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.ENABLE_MFA);
        return response;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.disableMfa")
    public UpdateRecordResponse disableMfa() throws SQLException {
        UpdateRecordResponse response = mfaService.disableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.DISABLE_MFA);
        return response;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.passkeys")
    public ApiStandardResponse<java.util.List<PasskeySummaryResponse>> passkeys()
            throws SQLException {
        return new ApiStandardResponse<>(
                passkeyService.list(AdminTokenThreadLocal.getUserId()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.passkeyRegistration")
    public ApiStandardResponse<PasskeyOptionsResponse<PasskeyRegistrationOptionsResponse>>
    passkeyRegistrationOptions() throws SQLException {
        PasskeyRegistrationOptionsRequest body = getRequestBodyWithNullCheck(PasskeyRegistrationOptionsRequest.class);
        return new ApiStandardResponse<>(
                passkeyService.startRegistration(AdminTokenThreadLocal.getUserId(), body, getRequest(), cacheService.getPublicWebSiteInfo().getTitle()));
    }

    @RefreshCache(updateStaticSites = StaticSiteType.ADMIN)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.addPasskey")
    public ApiStandardResponse<PasskeySummaryResponse> passkeyRegistrationVerify()
            throws SQLException {
        PasskeySummaryResponse summary = passkeyService.finishRegistration(AdminTokenThreadLocal.getUserId(),
                getRequestBodyWithNullCheck(PasskeyRegistrationVerifyRequest.class), getRequest());
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.REGISTER_PASSKEY, summary.getName());
        return new ApiStandardResponse<>(summary,
                I18nUtil.getAdminBackendStringFromRes("admin.passkey.register.success"));
    }

    @RefreshCache(updateStaticSites = StaticSiteType.ADMIN)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ACCOUNT_SELF, descriptionKey = "account.removePasskey")
    public ApiStandardResponse<Boolean> passkeyRemove() throws SQLException {
        PasskeyRemoveRequest body = getRequestBodyWithNullCheck(PasskeyRemoveRequest.class);
        passkeyService.remove(AdminTokenThreadLocal.getUserId(), body);
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.REMOVE_PASSKEY, String.valueOf(body.getId()));
        return new ApiStandardResponse<>(true,
                I18nUtil.getAdminBackendStringFromRes("admin.passkey.remove.success"));
    }
}
