package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import java.sql.SQLException;

public class OAuthAdminController extends BaseController {
    private void audit(com.zrlog.admin.business.type.AdminAuditAction action) { new com.zrlog.admin.business.service.AdminAuditService().record(request, action); }
    private final OAuthService service = new OAuthService();
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.list")
    public AdminPageDataResponse<Page> index() throws SQLException { return new AdminPageDataResponse<>(service.page(), "", request.getUri()); }
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.consent")
    public AdminPageDataResponse<Consent> consent() throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        return new AdminPageDataResponse<>(service.consent(getParamWithEmptyCheck("request_id")), "", request.getUri());
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.decide")
    public ApiStandardResponse<Redirect> decide() throws SQLException {
        service.requireSameOrigin(request.getHeader("Origin"));
        Redirect result = service.decide(getRequestBodyWithNullCheck(Decision.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.AUTHORIZE_APPLICATION);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.register")
    public ApiStandardResponse<Client> register() throws SQLException {
        service.requireSameOrigin(request.getHeader("Origin"));
        Client result = service.register(getRequestBodyWithNullCheck(Client.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.REGISTER_APPLICATION);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokeGrant")
    public ApiStandardResponse<Boolean> revokeGrant() throws SQLException {
        service.requireSameOrigin(request.getHeader("Origin"));
        service.revokeGrant(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(com.zrlog.admin.business.type.AdminAuditAction.REVOKE_APPLICATION);
        return new ApiStandardResponse<>(true);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.createPersonalToken")
    public ApiStandardResponse<com.zrlog.admin.business.security.PersonalTokenModels.Created> createPersonalToken() throws SQLException {
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("Pragma", "no-cache");
        service.requireSameOrigin(request.getHeader("Origin"));
        com.zrlog.admin.business.security.PersonalTokenModels.Created result =
                new com.zrlog.admin.business.service.PersonalAccessTokenService(service.mcpResource())
                        .create(getRequestBodyWithNullCheck(com.zrlog.admin.business.security.PersonalTokenModels.Create.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.CREATE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_GRANT_MANAGE, descriptionKey = "oauth.revokePersonalToken")
    public ApiStandardResponse<Boolean> revokePersonalToken() throws SQLException {
        service.requireSameOrigin(request.getHeader("Origin"));
        new com.zrlog.admin.business.service.PersonalAccessTokenService(service.mcpResource())
                .revoke(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(com.zrlog.admin.business.type.AdminAuditAction.REVOKE_PERSONAL_TOKEN);
        return new ApiStandardResponse<>(true);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OAUTH_CLIENT_MANAGE, descriptionKey = "oauth.disableClient")
    public ApiStandardResponse<Boolean> disableClient() throws SQLException {
        service.requireSameOrigin(request.getHeader("Origin"));
        service.disableClient(getRequestBodyWithNullCheck(Revoke.class).id);
        audit(com.zrlog.admin.business.type.AdminAuditAction.REVOKE_APPLICATION);
        return new ApiStandardResponse<>(true);
    }
}
