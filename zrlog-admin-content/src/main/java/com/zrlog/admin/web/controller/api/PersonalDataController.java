package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.PersonalDataService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.sql.SQLException;

public class PersonalDataController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final AdminAuditService adminAuditService;

    @Inject
    public PersonalDataController(
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            PersonalDataService personalDataService,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;
        this.personalDataService = personalDataService;

        this.pages = pages;
    }

    private final AdminPageDataFactory pages;

    private final PersonalDataService personalDataService;

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "privacy.page")
    public AdminPageDataResponse<PersonalDataPreviewResponse> index() {
        return pages.response(requestIdentity.optional(), new PersonalDataPreviewResponse(), "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "privacy.preview")
    public ApiStandardResponse<PersonalDataPreviewResponse> preview() throws SQLException {
        requirePost();
        PersonalDataPreviewRequest body = getRequestBodyWithNullCheck(PersonalDataPreviewRequest.class);
        return new ApiStandardResponse<>(personalDataService.preview(body, AdminTokenThreadLocal.getUserId()));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "privacy.exportComments")
    public ApiStandardResponse<PersonalDataCommentExportResponse> exportComments() throws SQLException {
        requirePost();
        PersonalDataPreviewRequest body = getRequestBodyWithNullCheck(PersonalDataPreviewRequest.class);
        PersonalDataCommentExportResponse response = personalDataService.exportComments(body);
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.EXPORT_PERSONAL_DATA);
        return new ApiStandardResponse<>(response);
    }

    private void requirePost() {
        if (request.getMethod() != HttpMethod.POST) {
            throw new ArgsException("method");
        }
    }
}
