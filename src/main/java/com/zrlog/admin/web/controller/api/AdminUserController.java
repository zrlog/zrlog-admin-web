package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.MfaService;
import com.zrlog.admin.business.service.PasskeyService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;
import java.util.Objects;

public class AdminUserController extends BaseController {

    private final UserService userService;
    private final MfaService mfaService;
    private final PasskeyService passkeyService;

    public AdminUserController() {
        this.userService = new UserService();
        this.mfaService = new MfaService();
        this.passkeyService = new PasskeyService();
    }

    public AdminUserController(HttpRequest request, HttpResponse response) {
        super(request, response);
        this.userService = new UserService();
        this.mfaService = new MfaService();
        this.passkeyService = new PasskeyService();
    }

    @ResponseBody
    public AdminPageDataResponse<UserBasicInfoResponse> index() throws SQLException {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return new AdminPageDataResponse<>(userService.getBasicUserInfo(adminTokenVO.getUserId(), adminTokenVO.getSessionId()), "", request.getUri());
    }

    /**
     * 校验是否处于登录状态，不需要返回过多的用户信息，可以返回一些全局需要使用到的与用户相关的信息，比如 头像/昵称,新版本
     *
     * @return 基础的用户信息
     */
    @ResponseBody
    public AdminPageDataResponse<UserInfoResponse> info() throws SQLException {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return new AdminPageDataResponse<>(userService.getUserInfoWithCache(adminTokenVO.getUserId(), adminTokenVO.getSessionId()), "", request.getUri());
    }

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse update() throws SQLException {
        UpdateAdminRequest updateAdminRequest = getRequestBodyWithNullCheck(UpdateAdminRequest.class);
        userService.update(AdminTokenThreadLocal.getUserId(), updateAdminRequest, getRequest());
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_PROFILE);
        UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse(true);
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.user.update.success"));
        return updateRecordResponse;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse updatePassword() throws SQLException {
        UpdateRecordResponse response = userService.updatePassword(AdminTokenThreadLocal.getUserId(),
                getRequestBodyWithNullCheck(UpdatePasswordRequest.class));
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_PASSWORD);
        return response;
    }

    @ResponseBody
    public AdminPageDataResponse<MfaStatusResponse> mfa() throws SQLException {
        return new AdminPageDataResponse<>(mfaService.getMfaStatus(AdminTokenThreadLocal.getUserId()), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse enableMfa() throws SQLException {
        UpdateRecordResponse response = mfaService.enableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
        new AdminAuditService().record(request, AdminAuditAction.ENABLE_MFA);
        return response;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse disableMfa() throws SQLException {
        UpdateRecordResponse response = mfaService.disableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
        new AdminAuditService().record(request, AdminAuditAction.DISABLE_MFA);
        return response;
    }

    @ResponseBody
    public ApiStandardResponse<java.util.List<PasskeySummaryResponse>> passkeys()
            throws SQLException {
        return new ApiStandardResponse<>(
                passkeyService.list(AdminTokenThreadLocal.getUserId()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public ApiStandardResponse<PasskeyOptionsResponse<PasskeyRegistrationOptionsResponse>>
    passkeyRegistrationOptions() throws SQLException {
        PasskeyRegistrationOptionsRequest body = getRequestBodyWithNullCheck(PasskeyRegistrationOptionsRequest.class);
        return new ApiStandardResponse<>(
                passkeyService.startRegistration(AdminTokenThreadLocal.getUserId(), body, getRequest()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public ApiStandardResponse<PasskeySummaryResponse> passkeyRegistrationVerify()
            throws SQLException {
        PasskeySummaryResponse summary = passkeyService.finishRegistration(AdminTokenThreadLocal.getUserId(),
                getRequestBodyWithNullCheck(PasskeyRegistrationVerifyRequest.class), getRequest());
        new AdminAuditService().record(request, AdminAuditAction.REGISTER_PASSKEY, summary.getName());
        return new ApiStandardResponse<>(summary,
                I18nUtil.getAdminBackendStringFromRes("admin.passkey.register.success"));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public ApiStandardResponse<Boolean> passkeyRemove() throws SQLException {
        PasskeyRemoveRequest body = getRequestBodyWithNullCheck(PasskeyRemoveRequest.class);
        passkeyService.remove(AdminTokenThreadLocal.getUserId(), body);
        new AdminAuditService().record(request, AdminAuditAction.REMOVE_PASSKEY, String.valueOf(body.getId()));
        return new ApiStandardResponse<>(true,
                I18nUtil.getAdminBackendStringFromRes("admin.passkey.remove.success"));
    }
}
