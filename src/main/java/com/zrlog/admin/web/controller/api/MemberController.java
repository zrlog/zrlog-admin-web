package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.security.MemberModels.*;
import com.zrlog.admin.business.service.MemberService;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import java.sql.SQLException;

public class MemberController extends BaseController {
    private void audit(com.zrlog.admin.business.type.AdminAuditAction action) { new com.zrlog.admin.business.service.AdminAuditService().record(request, action); }
    private final MemberService service = new MemberService();
    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, descriptionKey = "member.list")
    public AdminPageDataResponse<Page> index() throws SQLException { return new AdminPageDataResponse<>(service.list(), "", request.getUri()); }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.create")
    public ApiStandardResponse<Member> create() throws SQLException {
        new OAuthService().requireSameOrigin(request.getHeader("Origin"));
        Member result = service.create(getRequestBodyWithNullCheck(Create.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OWNERSHIP_TRANSFER, descriptionKey = "member.transferOwnership")
    public ApiStandardResponse<Boolean> transfer() throws SQLException {
        new OAuthService().requireSameOrigin(request.getHeader("Origin"));
        service.transfer(getRequestBodyWithNullCheck(Transfer.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.TRANSFER_OWNERSHIP);
        return new ApiStandardResponse<>(true);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.update")
    public ApiStandardResponse<Member> update() throws SQLException {
        new OAuthService().requireSameOrigin(request.getHeader("Origin"));
        Member result = service.update(getRequestBodyWithNullCheck(Update.class));
        audit(com.zrlog.admin.business.type.AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }
}
