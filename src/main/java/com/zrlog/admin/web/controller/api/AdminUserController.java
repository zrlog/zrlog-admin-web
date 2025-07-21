package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.admin.business.rest.request.UpdateMfaRequest;
import com.zrlog.admin.business.rest.request.UpdateAdminRequest;
import com.zrlog.admin.business.rest.request.UpdatePasswordRequest;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.business.rest.response.UserInfoResponse;
import com.zrlog.admin.business.service.MfaService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.I18nUtil;

import java.sql.SQLException;
import java.util.Objects;

public class AdminUserController extends BaseController {

    private final UserService userService;
    private final MfaService mfaService;

    public AdminUserController() {
        this.userService = new UserService();
        this.mfaService = new MfaService();
    }

    public AdminUserController(HttpRequest request, HttpResponse response) {
        super(request, response);
        this.userService = new UserService();
        this.mfaService = new MfaService();
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<UserBasicInfoResponse> index() throws SQLException {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return new AdminApiPageDataStandardResponse<>(userService.getBasicUserInfo(adminTokenVO.getUserId(), adminTokenVO.getSessionId()), "", request.getUri());
    }

    /**
     * 校验是否处于登录状态，不需要返回过多的用户信息，可以返回一些全局需要使用到的与用户相关的信息，比如 头像/昵称,新版本
     *
     * @return 基础的用户信息
     */
    @ResponseBody
    public AdminApiPageDataStandardResponse<UserInfoResponse> info() {
        AdminTokenVO adminTokenVO = AdminTokenThreadLocal.getUser();
        if (Objects.isNull(adminTokenVO)) {
            throw new AdminAuthException();
        }
        return new AdminApiPageDataStandardResponse<>(userService.getUserInfoWithCache(adminTokenVO.getUserId(), adminTokenVO.getSessionId()), "", request.getUri());
    }

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse update() throws SQLException {
        UpdateAdminRequest updateAdminRequest = getRequestBodyWithNullCheck(UpdateAdminRequest.class);
        userService.update(AdminTokenThreadLocal.getUserId(), updateAdminRequest);
        UpdateRecordResponse updateRecordResponse = new UpdateRecordResponse(true);
        updateRecordResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("updatePersonInfoSuccess"));
        return updateRecordResponse;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse updatePassword() throws SQLException {
        return userService.updatePassword(AdminTokenThreadLocal.getUserId(),
                getRequestBodyWithNullCheck(UpdatePasswordRequest.class));
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<MfaStatusResponse> mfa() throws SQLException {
        return new AdminApiPageDataStandardResponse<>(mfaService.getMfaStatus(AdminTokenThreadLocal.getUserId()), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse enableMfa() throws SQLException {
        return mfaService.enableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse disableMfa() throws SQLException {
        return mfaService.disableMfa(AdminTokenThreadLocal.getUserId(), getRequestBodyWithNullCheck(UpdateMfaRequest.class));
    }
}
