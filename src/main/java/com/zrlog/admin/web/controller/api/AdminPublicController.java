package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.PublicVersionResponse;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.util.Objects;

import static com.zrlog.util.CrossUtils.cross;

public class AdminPublicController extends BaseController {

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.resources")
    public ApiStandardResponse<AdminResourceInfoResponse> adminResource() {
        cross(request, response);
        return new ApiStandardResponse<>(AdminConstants.adminResource.adminResourceInfo(request));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "system.publicVersion")
    public ApiStandardResponse<PublicVersionResponse> version() {
        cross(request, response);
        if (Objects.equals(BlogBuildInfoUtil.getBuildId(), request.getParaToStr("buildId", ""))) {
            PublicVersionResponse versionResponse = new PublicVersionResponse();
            versionResponse.setBuildId(BlogBuildInfoUtil.getBuildId());
            return new ApiStandardResponse<>(versionResponse, I18nUtil.getAdminBackendStringFromRes("admin.upgrade.success"));
        }
        return new ApiStandardResponse<>(new PublicVersionResponse());
    }
}
