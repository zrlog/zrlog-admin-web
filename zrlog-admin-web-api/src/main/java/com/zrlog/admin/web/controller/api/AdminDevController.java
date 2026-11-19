package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DevInfoResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.rest.response.WebsiteKvEntryResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.LockVO;
import com.zrlog.data.cache.WebsiteCacheService;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.util.LockManager;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdminDevController extends Controller {
    private final AdminRequestIdentity requestIdentity;

    private final AdminAuditService adminAuditService;
    private final WebsiteCacheService websiteCacheService;
    private final LockManager lockManager;
    private final AdminPageDataFactory pages;

    @Inject
    public AdminDevController(
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            WebsiteCacheService websiteCacheService,
            LockManager lockManager,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;
        this.websiteCacheService = websiteCacheService;
        this.lockManager = lockManager;

        this.pages = pages;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.development")
    public AdminPageDataResponse<Boolean> ui() {
        // A ready marker for page hydration; component fixtures live entirely in the browser.
        return pages.response(requestIdentity.optional(), Boolean.TRUE, "", "/admin/dev/ui");
    }

    @ResponseBody
    @RequestMethod
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.releaseLocks")
    public UpdateRecordResponse releaseLocks() throws Exception {
        List<LockVO> lockDTOS = lockManager.getLocks();
        for (LockVO lockDTO : lockDTOS) {
            lockManager.releaseLock(lockDTO.getName());
        }
        this.adminAuditService.record(
                AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                AdminAuditAction.RELEASE_DEV_LOCKS,
                String.valueOf(lockDTOS.size()));
        return new UpdateRecordResponse(true);
    }

    @ResponseBody
    @RequestMethod
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.development")
    public AdminPageDataResponse<DevInfoResponse> index() throws Exception {
        DevInfoResponse devInfoResponse = new DevInfoResponse();
        devInfoResponse.setLocks(lockManager.getLocks());
        devInfoResponse.setCacheEntries(
                websiteCacheService.listEntries().stream()
                        .map(AdminDevController::cacheEntryResponse)
                        .collect(Collectors.toList()));
        devInfoResponse.setDevMode(EnvKit.isDevMode());
        return pages.response(
                requestIdentity.optional(), devInfoResponse, "", getRequest().getUri());
    }

    private static WebsiteKvEntryResponse cacheEntryResponse(WebsiteCacheService.Entry row) {
        WebsiteKvEntryResponse response = new WebsiteKvEntryResponse();
        response.setKey(row.name);
        response.setValue(Objects.toString(row.value, ""));
        response.setSize(row.size);
        return response;
    }

    @ResponseBody
    @RequiresAction(
            value = AccountAction.SYSTEM_MANAGE,
            descriptionKey = "system.enableDevelopment")
    public ApiStandardResponse<Void> enable() {
        setDevMode(true);
        this.adminAuditService.record(
                AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                AdminAuditAction.ENABLE_DEV_MODE);
        return new ApiStandardResponse<>();
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.setDevelopment")
    public ApiStandardResponse<Boolean> mode() {
        boolean enabled = request.getParaToBool("enabled", false);
        setDevMode(enabled);
        this.adminAuditService.record(
                AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                enabled ? AdminAuditAction.ENABLE_DEV_MODE : AdminAuditAction.DISABLE_DEV_MODE);
        return new ApiStandardResponse<>(
                EnvKit.isDevMode(),
                I18nUtil.getAdminBackendStringFromRes(
                        EnvKit.isDevMode()
                                ? "admin.system.development.enable.success"
                                : "admin.system.development.disable.success"));
    }

    private void setDevMode(boolean enabled) {
        if (!enabled) {
            System.getProperties().remove("sws.run.mode");
            DevKit.disableDev(request.getServerConfig());
            return;
        }
        if (!request.getServerConfig().isNativeImageAgent()) {
            System.getProperties().put("sws.run.mode", "dev");
        }
        DevKit.configDev(request.getServerConfig());
    }
}
