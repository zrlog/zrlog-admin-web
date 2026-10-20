package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.security.MemberModels.*;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.MemberService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.security.AdminRouteAuthorizer;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.sql.SQLException;

public class MemberController extends BaseController {
    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;

    private final AdminRouteAuthorizer authorizer;
    private final AdminRequestIdentity requestIdentity;
    private final MemberService service;

    @Inject
    public MemberController(
            AdminRouteAuthorizer authorizer,
            AdminRequestIdentity requestIdentity,
            AdminPageDataFactory pages,
            MemberService service,
            AdminAuditService adminAuditService) {
        this.service = service;
        this.adminAuditService = adminAuditService;

        this.pages = pages;
        this.authorizer = authorizer;
        this.requestIdentity = requestIdentity;
    }

    private void audit(AdminAuditAction action) {
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), action);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, descriptionKey = "member.list")
    public AdminPageDataResponse<Page> index() throws SQLException {
        var actor = requestIdentity.current();
        return pages.response(actor, service.list(actor), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.create")
    public ApiStandardResponse<Member> create() throws SQLException {
        authorizer.requireAdminOrigin(request);
        Member result = service.create(requestIdentity.current(), getRequestBodyWithNullCheck(Create.class));
        audit(AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OWNERSHIP_TRANSFER, descriptionKey = "member.transferOwnership")
    public ApiStandardResponse<Boolean> transfer() throws SQLException {
        authorizer.requireAdminOrigin(request);
        service.transfer(requestIdentity.current(), getRequestBodyWithNullCheck(Transfer.class));
        audit(AdminAuditAction.TRANSFER_OWNERSHIP);
        return new ApiStandardResponse<>(true);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.update")
    public ApiStandardResponse<Member> update() throws SQLException {
        authorizer.requireAdminOrigin(request);
        Member result = service.update(requestIdentity.current(), getRequestBodyWithNullCheck(Update.class));
        audit(AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }
}
