package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.sql.SQLException;

public class OAuthAdminController extends BaseController {
    private final PersonalAccessTokenService.Factory personalTokens;

    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;
    private final OAuthService service;
    private final AdminRequestIdentity requestIdentity;

    @Inject
    public OAuthAdminController(
            OAuthService service,
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            PersonalAccessTokenService.Factory personalTokens,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.personalTokens = personalTokens;

        this.adminAuditService = adminAuditService;

        this.pages = pages;
        this.service = service;
    }

    private void audit(AccountAccess actor, AdminAuditAction action) {
        this.adminAuditService.record(AdminAuditContexts.from(request, actor.getUserId()), action);
    }

    private GrantAuthority grantAuthority() {
        return new GrantAuthority(requestIdentity.current(), !DelegatedAccess.restricted());
    }

    private String sessionId() {
        return AdminTokenThreadLocal.getUser().getSessionId();
    }

    private <T> AdminPageDataResponse<T> page(AccountAccess actor, T data) {
        return new AdminPageDataResponse<>(data, "", pages.metadata(actor, request.getUri()));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.list")
    public AdminPageDataResponse<Page> index() throws SQLException {
        var actor = requestIdentity.current();
        return page(actor, service.page(actor));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.list")
    public AdminPageDataResponse<Page> clients() throws SQLException {
        var actor = requestIdentity.current();
        return page(actor, service.page(actor));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.consent")
    public AdminPageDataResponse<Consent> consent() throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        var authority = grantAuthority();
        return page(authority.actor(), service.consent(authority, sessionId(), getParamWithEmptyCheck("request_id")));
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.decide")
    public ApiStandardResponse<Redirect> decide() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        var authority = grantAuthority();
        Redirect result = service.decide(authority, sessionId(), getRequestBodyWithNullCheck(Decision.class));
        audit(authority.actor(), AdminAuditAction.AUTHORIZE_APPLICATION);
        return new ApiStandardResponse<>(result);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.register")
    public ApiStandardResponse<Client> register() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        var actor = requestIdentity.current();
        Client result = service.register(actor, getRequestBodyWithNullCheck(Client.class));
        audit(actor, AdminAuditAction.REGISTER_APPLICATION);
        return new ApiStandardResponse<>(result);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokeGrant")
    public ApiStandardResponse<Boolean> revokeGrant() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        var actor = requestIdentity.current();
        service.revokeGrant(actor, getRequestBodyWithNullCheck(Revoke.class).id);
        audit(actor, AdminAuditAction.REVOKE_APPLICATION);
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
        var authority = grantAuthority();
        PersonalTokenModels.Created result =
                personalTokens
                        .create(service.mcpResource())
                        .create(authority, getRequestBodyWithNullCheck(PersonalTokenModels.Create.class));
        audit(authority.actor(), AdminAuditAction.CREATE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(result);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokePersonalToken")
    public ApiStandardResponse<Boolean> revokePersonalToken() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        var actor = requestIdentity.current();
        personalTokens
                .create(service.mcpResource())
                .revoke(actor, getRequestBodyWithNullCheck(Revoke.class).id);
        audit(actor, AdminAuditAction.REVOKE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(true);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.disableClient")
    public ApiStandardResponse<Boolean> disableClient() throws SQLException {
        service.requireAdminOrigin(request.getHeader("Origin"));
        var actor = requestIdentity.current();
        service.disableClient(actor, getRequestBodyWithNullCheck(Revoke.class).id);
        audit(actor, AdminAuditAction.REVOKE_APPLICATION);
        return new ApiStandardResponse<>(true);
    }
}
