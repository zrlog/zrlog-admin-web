package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.PublicVersionResponse;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static com.zrlog.util.CrossUtils.cross;

public class AdminPublicController extends BaseController {
    private final AdminResourceService resources;

    private final BooleanSupplier staticHtml;

    @Inject
    public AdminPublicController(AdminResourceService resources, BooleanSupplier staticHtml) {
        this.staticHtml = Objects.requireNonNull(staticHtml);
        this.resources = resources;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.resources")
    public ApiStandardResponse<AdminResourceInfoResponse> adminResource() {
        cross(request, response, staticHtml.getAsBoolean());
        return new ApiStandardResponse<>(resources.adminResourceInfo(request));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "system.publicVersion")
    public ApiStandardResponse<PublicVersionResponse> version() {
        cross(request, response, staticHtml.getAsBoolean());
        if (Objects.equals(BlogBuildInfoUtil.getBuildId(), request.getParaToStr("buildId", ""))) {
            PublicVersionResponse versionResponse = new PublicVersionResponse();
            versionResponse.setBuildId(BlogBuildInfoUtil.getBuildId());
            return new ApiStandardResponse<>(versionResponse, I18nUtil.getAdminBackendStringFromRes("admin.upgrade.success"));
        }
        return new ApiStandardResponse<>(new PublicVersionResponse());
    }
}
