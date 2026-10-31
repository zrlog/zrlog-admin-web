package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.rest.response.SystemResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.util.SystemInfoUtils;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

public class AdminSystemController extends Controller {
    private final AdminRequestIdentity requestIdentity;

    private final AdminPageDataFactory pages;


    private final SystemInfoUtils systemInfo;
    private final AdminAuditService audit;

    @Inject
    public AdminSystemController(SystemInfoUtils systemInfo, AdminPageDataFactory pages,
                                 AdminRequestIdentity requestIdentity, AdminAuditService audit) {
        this.audit = audit;
        this.requestIdentity = requestIdentity;
        this.pages = pages;
        this.systemInfo = systemInfo;
    }

    @ResponseBody
    @com.hibegin.http.annotation.RequestMethod(method = com.hibegin.http.HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.audit")
    public AdminPageDataResponse<List<AdminAuditLogEntryResponse>> audit() {
        var actor = requestIdentity.current();
        return pages.response(actor, audit.getRecentLogs(actor), "", AdminConstants.ADMIN_URI_BASE_PATH + "/system/audit");
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.status")
    public AdminPageDataResponse<SystemResponse> index() throws SQLException {
        return pages.response(requestIdentity.optional(),
                new SystemResponse(systemInfo.systemIOInfoVO(), systemInfo.serverInfo(getRequest()), ZrLogUtil.isDockerMode(), EnvKit.isNativeImage()),
                "", AdminConstants.ADMIN_URI_BASE_PATH + "/system");
    }
}
