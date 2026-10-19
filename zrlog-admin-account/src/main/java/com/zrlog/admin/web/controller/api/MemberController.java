package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.security.MemberModels.*;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.MemberService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.sql.SQLException;

public class MemberController extends BaseController {
    private final AdminAuditService adminAuditService;

    private final AdminPageDataFactory pages;

    private final BackendServerUrl backend;

    @Inject
    public MemberController(
            BackendServerUrl backend,
            AdminPageDataFactory pages,
            MemberService service,
            AdminAuditService adminAuditService) {
        this.service = service;
        this.adminAuditService = adminAuditService;

        this.pages = pages;
        this.backend = backend;
    }

    private void audit(AdminAuditAction action) {
        this.adminAuditService.record(request, action);
    }

    private final MemberService service;

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, descriptionKey = "member.list")
    public AdminPageDataResponse<Page> index() throws SQLException { return pages.response(service.list(), "", request.getUri()); }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.create")
    public ApiStandardResponse<Member> create() throws SQLException {
        AccountPermissionService.requireAdminOrigin(request, backend);
        Member result = service.create(getRequestBodyWithNullCheck(Create.class));
        audit(AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.OWNERSHIP_TRANSFER, descriptionKey = "member.transferOwnership")
    public ApiStandardResponse<Boolean> transfer() throws SQLException {
        AccountPermissionService.requireAdminOrigin(request, backend);
        service.transfer(getRequestBodyWithNullCheck(Transfer.class));
        audit(AdminAuditAction.TRANSFER_OWNERSHIP);
        return new ApiStandardResponse<>(true);
    }
    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.MEMBER_MANAGE, conditional = AccountAction.ADMIN_APPOINT, descriptionKey = "member.update")
    public ApiStandardResponse<Member> update() throws SQLException {
        AccountPermissionService.requireAdminOrigin(request, backend);
        Member result = service.update(getRequestBodyWithNullCheck(Update.class));
        audit(AdminAuditAction.UPDATE_MEMBER);
        return new ApiStandardResponse<>(result);
    }
}
