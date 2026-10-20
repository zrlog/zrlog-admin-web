package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.request.PasskeyAuthenticationVerifyRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.PasskeyAuthenticationOptionsResponse;
import com.zrlog.admin.business.rest.response.PasskeyOptionsResponse;
import com.zrlog.admin.business.rest.response.UserBasicInfoResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.PasskeyService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.common.TokenService;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class AccountSessionController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;

    private final UserService userService;
    private final TokenService tokenService;
    private final BooleanSupplier installed;
    private final PasskeyService passkeyService;

    @Inject
    public AccountSessionController(
            UserService userService,
            TokenService tokenService,
            BooleanSupplier installed,
            AdminPageDataFactory pages,
            PasskeyService passkeyService,
            AdminAuditService adminAuditService,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;

        this.passkeyService = passkeyService;
        this.pages = pages;
        this.userService = userService;
        this.tokenService = tokenService;
        this.installed = installed;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.login")
    public AdminPageDataResponse<UserBasicInfoResponse> login() throws Exception {
        if (!installed.getAsBoolean()) {
            throw new MissingInstallException();
        }
        LoginRequest loginRequest = getRequestBodyWithNullCheck(LoginRequest.class);
        UserLoginDTO dto = userService.login(loginRequest);
        tokenService.setAdminToken(dto.getId(), dto.getSecretKey(), dto.getUserBasicInfoResponse().getKey(),
                Objects.equals(loginRequest.getHttps(), true) ? "https" : "http", getRequest(), getResponse());
        this.adminAuditService.record(AdminAuditContexts.from(request, dto.getId()),
                AdminAuditAction.LOGIN_SUCCESS,
                dto.getUserBasicInfoResponse().getUserName());
        return pages.response(requestIdentity.optional(), dto.getUserBasicInfoResponse());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.passkeyChallenge")
    public ApiStandardResponse<PasskeyOptionsResponse<PasskeyAuthenticationOptionsResponse>> passkeyAuthenticationOptions()
            throws SQLException {
        if (!installed.getAsBoolean()) {
            throw new MissingInstallException();
        }
        return new ApiStandardResponse<>(passkeyService.startAuthentication(getRequest()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.passkeyLogin")
    public AdminPageDataResponse<UserBasicInfoResponse> passkeyAuthenticationVerify() throws Exception {
        if (!installed.getAsBoolean()) {
            throw new MissingInstallException();
        }
        UserLoginDTO dto = passkeyService.finishAuthentication(
                getRequestBodyWithNullCheck(PasskeyAuthenticationVerifyRequest.class), getRequest());
        String protocol = passkeyService.requestProtocol(getRequest());
        tokenService.setAdminToken(dto.getId(), dto.getSecretKey(),
                dto.getUserBasicInfoResponse().getKey(), protocol, getRequest(), getResponse());
        this.adminAuditService.record(AdminAuditContexts.from(request, dto.getId()),
                AdminAuditAction.LOGIN_WITH_PASSKEY,
                dto.getUserBasicInfoResponse().getUserName());
        return pages.response(requestIdentity.optional(), dto.getUserBasicInfoResponse());
    }

}
