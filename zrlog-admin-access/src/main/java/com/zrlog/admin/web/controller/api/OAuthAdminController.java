package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.sql.SQLException;

public class OAuthAdminController extends BaseController {
    private final PersonalAccessTokenService.Factory personalTokens;

    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;

    private void audit(AdminAuditAction action) {
        this.adminAuditService.record(request, action);
    }

    private final OAuthService service;

    @Inject
    public OAuthAdminController(
            OAuthService service,
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            PersonalAccessTokenService.Factory personalTokens) {
        this.personalTokens = personalTokens;

        this.adminAuditService = adminAuditService;

        this.pages = pages; this.service = service; }
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.list")
    public AdminPageDataResponse<Page> index() throws SQLException { return pages.response(service.page(), "", request.getUri()); }
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.list")
    public AdminPageDataResponse<Page> clients() throws SQLException { return pages.response(service.page(), "", request.getUri()); }
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.consent")
    public AdminPageDataResponse<Consent> consent() throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        return pages.response(service.consent(getParamWithEmptyCheck("request_id")), "", request.getUri());
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.decide")
    public ApiStandardResponse<Redirect> decide() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        Redirect result = service.decide(getRequestBodyWithNullCheck(Decision.class));
        audit(AdminAuditAction.AUTHORIZE_APPLICATION);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.register")
    public ApiStandardResponse<Client> register() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        Client result = service.register(getRequestBodyWithNullCheck(Client.class));
        audit(AdminAuditAction.REGISTER_APPLICATION);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokeGrant")
    public ApiStandardResponse<Boolean> revokeGrant() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        service.revokeGrant(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(AdminAuditAction.REVOKE_APPLICATION);
        return new ApiStandardResponse<>(true);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(
            value = AccountAction.OAUTH_GRANT_MANAGE,
            descriptionKey = "oauth.createPersonalToken")
    public ApiStandardResponse<PersonalTokenModels.Created> createPersonalToken()
            throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Pragma", "no-cache");
        service.requireAdminOrigin(request.getHeader("Origin"));
        PersonalTokenModels.Created result =
                personalTokens
                        .create(service.mcpResource())
                        .create(getRequestBodyWithNullCheck(PersonalTokenModels.Create.class));
        audit(AdminAuditAction.CREATE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokePersonalToken")
    public ApiStandardResponse<Boolean> revokePersonalToken() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        personalTokens
                .create(service.mcpResource())
                .revoke(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(AdminAuditAction.REVOKE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(true);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.disableClient")
    public ApiStandardResponse<Boolean> disableClient() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        service.disableClient(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(AdminAuditAction.REVOKE_APPLICATION);
        return new ApiStandardResponse<>(true);
    }
}
