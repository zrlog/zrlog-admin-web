package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.PluginInfoResponse;
import com.zrlog.admin.business.rest.response.PublicVersionResponse;
import com.zrlog.common.controller.BaseController;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.zrlog.util.CrossUtils.cross;

public class AdminPublicController extends BaseController {

    @ResponseBody
    public AdminApiPageDataStandardResponse<Map<String, Object>> adminResource() {
        cross(request, response);
        return new AdminApiPageDataStandardResponse<>(AdminConstants.adminResource.adminResourceInfo(request));
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<PublicVersionResponse> version() {
        cross(request, response);
        if (Objects.equals(BlogBuildInfoUtil.getBuildId(), request.getParaToStr("buildId", ""))) {
            PublicVersionResponse versionResponse = new PublicVersionResponse();
            versionResponse.setBuildId(BlogBuildInfoUtil.getBuildId());
            versionResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("upgradeSuccess"));
            return new AdminApiPageDataStandardResponse<>(versionResponse);
        }
        return new AdminApiPageDataStandardResponse<>(new PublicVersionResponse());
    }
}
