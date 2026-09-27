package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.request.PasskeyAuthenticationVerifyRequest;
import com.zrlog.admin.business.rest.request.FirstUseChecklistRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class AccountSessionController extends BaseController {
    private final UserService userService = new UserService();
    private final PasskeyService passkeyService = new PasskeyService();

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.login")
    public AdminPageDataResponse<UserBasicInfoResponse> login() throws Exception {
        if (!Constants.zrLogConfig.isInstalled()) {
            throw new MissingInstallException();
        }
        LoginRequest loginRequest = getRequestBodyWithNullCheck(LoginRequest.class);
        UserLoginDTO dto = userService.login(loginRequest);
        Constants.zrLogConfig.getTokenService().setAdminToken(dto.getId(), dto.getSecretKey(), dto.getUserBasicInfoResponse().getKey(),
                Objects.equals(loginRequest.getHttps(), true) ? "https" : "http", getRequest(), getResponse());
        new AdminAuditService().record(request, AdminAuditAction.LOGIN_SUCCESS, dto.getUserBasicInfoResponse().getUserName());
        return new AdminPageDataResponse<>(dto.getUserBasicInfoResponse());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.passkeyChallenge")
    public ApiStandardResponse<PasskeyOptionsResponse<PasskeyAuthenticationOptionsResponse>> passkeyAuthenticationOptions()
            throws SQLException {
        if (!Constants.zrLogConfig.isInstalled()) {
            throw new MissingInstallException();
        }
        return new ApiStandardResponse<>(passkeyService.startAuthentication(getRequest()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.passkeyLogin")
    public AdminPageDataResponse<UserBasicInfoResponse> passkeyAuthenticationVerify() throws Exception {
        if (!Constants.zrLogConfig.isInstalled()) {
            throw new MissingInstallException();
        }
        UserLoginDTO dto = passkeyService.finishAuthentication(
                getRequestBodyWithNullCheck(PasskeyAuthenticationVerifyRequest.class), getRequest());
        String protocol = new PasskeyRequestContext().resolve(getRequest()).getProtocol();
        Constants.zrLogConfig.getTokenService().setAdminToken(dto.getId(), dto.getSecretKey(),
                dto.getUserBasicInfoResponse().getKey(), protocol, getRequest(), getResponse());
        new AdminAuditService().record(request, AdminAuditAction.LOGIN_WITH_PASSKEY,
                dto.getUserBasicInfoResponse().getUserName());
        return new AdminPageDataResponse<>(dto.getUserBasicInfoResponse());
    }


}
