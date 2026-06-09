package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.PersonalDataPreviewRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.PersonalDataCommentExportResponse;
import com.zrlog.admin.business.rest.response.PersonalDataPreviewResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.PersonalDataService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.sql.SQLException;

public class PersonalDataController extends BaseController {

    private final PersonalDataService personalDataService = new PersonalDataService();

    @ResponseBody
    public AdminPageDataResponse<PersonalDataPreviewResponse> index() {
        return new AdminPageDataResponse<>(new PersonalDataPreviewResponse(), "", request.getUri());
    }

    @ResponseBody
    public ApiStandardResponse<PersonalDataPreviewResponse> preview() throws SQLException {
        requirePost();
        PersonalDataPreviewRequest body = getRequestBodyWithNullCheck(PersonalDataPreviewRequest.class);
        return new ApiStandardResponse<>(personalDataService.preview(body, AdminTokenThreadLocal.getUserId()));
    }

    @ResponseBody
    public ApiStandardResponse<PersonalDataCommentExportResponse> exportComments() throws SQLException {
        requirePost();
        PersonalDataPreviewRequest body = getRequestBodyWithNullCheck(PersonalDataPreviewRequest.class);
        PersonalDataCommentExportResponse response = personalDataService.exportComments(body);
        new AdminAuditService().record(request, AdminAuditAction.EXPORT_PERSONAL_DATA);
        return new ApiStandardResponse<>(response);
    }

    private void requirePost() {
        if (request.getMethod() != HttpMethod.POST) {
            throw new ArgsException("method");
        }
    }
}
