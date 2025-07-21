package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.HealthCheckResponse;
import com.zrlog.admin.business.rest.response.SystemResponse;
import com.zrlog.admin.business.service.SystemHealthCheckService;
import com.zrlog.util.ZrLogUtil;

import java.sql.SQLException;

import static com.zrlog.admin.util.SystemInfoUtils.serverInfo;
import static com.zrlog.admin.util.SystemInfoUtils.systemIOInfoVO;

public class AdminSystemController extends Controller {

    private final SystemHealthCheckService systemHealthCheckService = new SystemHealthCheckService();

    @ResponseBody
    public AdminApiPageDataStandardResponse<SystemResponse> index() throws SQLException {
        return new AdminApiPageDataStandardResponse<>(
                new SystemResponse(systemIOInfoVO(), serverInfo(getRequest()), ZrLogUtil.isDockerMode(), EnvKit.isNativeImage()),
                "", request.getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<HealthCheckResponse> healthCheck() throws SQLException {
        return new AdminApiPageDataStandardResponse<>(systemHealthCheckService.check(), "", request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    public AdminApiPageDataStandardResponse<HealthCheckResponse> optimize() throws SQLException {
        return new AdminApiPageDataStandardResponse<>(systemHealthCheckService.optimize(), "", request.getUri());
    }
}
