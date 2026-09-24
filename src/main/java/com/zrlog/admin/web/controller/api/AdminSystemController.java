package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.SystemResponse;
import com.zrlog.util.ZrLogUtil;

import java.sql.SQLException;

import static com.zrlog.admin.util.SystemInfoUtils.serverInfo;
import static com.zrlog.admin.util.SystemInfoUtils.systemIOInfoVO;

public class AdminSystemController extends Controller {

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.status")
    public AdminPageDataResponse<SystemResponse> index() throws SQLException {
        return new AdminPageDataResponse<>(
                new SystemResponse(systemIOInfoVO(), serverInfo(getRequest()), ZrLogUtil.isDockerMode(), EnvKit.isNativeImage()),
                "", request.getUri());
    }
}
