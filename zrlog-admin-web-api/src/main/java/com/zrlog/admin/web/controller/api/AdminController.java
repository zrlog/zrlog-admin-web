package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.rest.request.LoginRequest;
import com.zrlog.admin.business.rest.request.PasskeyAuthenticationVerifyRequest;
import com.zrlog.admin.business.rest.request.FirstUseChecklistRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class AdminController extends BaseController {

    private final AdminDashboardService dashboardService = new AdminDashboardService();

    /**
     * 触发更新缓存
     */
    @ResponseBody
    @RefreshCache(updateStaticSites = {StaticSiteType.ADMIN, StaticSiteType.BLOG})
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.refreshCache")
    public UpdateRecordResponse refreshCache() {
        new AdminAuditService().record(request, AdminAuditAction.REFRESH_CACHE);
        return new UpdateRecordResponse();
    }


    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.error")
    public AdminPageDataResponse<ErrorPageResponse> error() {
        return new AdminPageDataResponse<>(new ErrorPageResponse(request.getParaToStr("message", "")), "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.PLUGIN_MANAGE, descriptionKey = "plugin.list")
    public AdminPageDataResponse<PluginInfoResponse> plugin() {
        String page = getRequest().getParaToStr("page", "");
        return new AdminPageDataResponse<>(new PluginInfoResponse("admin/plugins/" + page), "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.DASHBOARD_READ, descriptionKey = "dashboard.read")
    public AdminPageDataResponse<IndexResponse> index() throws SQLException {
        return new AdminPageDataResponse<>(dashboardService.loadIndex(request, AdminTokenThreadLocal.getUser()),
                "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.DASHBOARD_READ, descriptionKey = "dashboard.configure")
    public ApiStandardResponse<AdminDashboardConfigResponse> indexConfig() {
        AdminDashboardConfigResponse config;
        String message = "";
        if (request.getMethod() == HttpMethod.POST) {
            config = dashboardService.saveConfig(
                    getRequestBodyWithNullCheck(com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest.class),
                    request, AdminTokenThreadLocal.getUser());
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_DASHBOARD_CONFIG);
            message = I18nUtil.getAdminBackendStringFromRes("admin.common.update.success");
        } else {
            config = dashboardService.getConfig(request, AdminTokenThreadLocal.getUser());
        }
        return new ApiStandardResponse<>(config, message);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.DASHBOARD_READ, descriptionKey = "dashboard.dismissChecklist")
    public ApiStandardResponse<FirstUseChecklistResponse> dismissFirstUseChecklist() throws SQLException {
        FirstUseChecklistRequest body = getRequestBodyWithNullCheck(FirstUseChecklistRequest.class);
        return new ApiStandardResponse<>(dashboardService.dismissFirstUseChecklist(body.getVersion()));
    }
}
