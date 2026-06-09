package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DevInfoResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.WebsiteCacheService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.DevKit;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.LockVO;
import com.zrlog.data.util.DistributedLockManager;

import java.util.List;

public class AdminDevController extends Controller {

    @ResponseBody
    @RequestMethod
    public UpdateRecordResponse releaseLocks() throws Exception {
        List<LockVO> lockDTOS = DistributedLockManager.getInstance().getLocks();
        for (LockVO lockDTO : lockDTOS) {
            DistributedLockManager.getInstance().releaseLock(lockDTO.getName());
        }
        new AdminAuditService().record(request, AdminAuditAction.RELEASE_DEV_LOCKS,
                String.valueOf(lockDTOS.size()));
        return new UpdateRecordResponse(true);
    }

    @ResponseBody
    @RequestMethod
    public AdminPageDataResponse<DevInfoResponse> index() throws Exception {
        DevInfoResponse devInfoResponse = new DevInfoResponse();
        devInfoResponse.setLocks(DistributedLockManager.getInstance().getLocks());
        devInfoResponse.setCacheEntries(new WebsiteCacheService().listEntries());
        devInfoResponse.setDevMode(EnvKit.isDevMode());
        return new AdminPageDataResponse<>(devInfoResponse, "", getRequest().getUri());
    }

    @ResponseBody
    public ApiStandardResponse<Void> enable() {
        setDevMode(true);
        new AdminAuditService().record(request, AdminAuditAction.ENABLE_DEV_MODE);
        return new ApiStandardResponse<>();
    }

    @ResponseBody
    public ApiStandardResponse<Boolean> mode() {
        boolean enabled = request.getParaToBool("enabled", false);
        setDevMode(enabled);
        new AdminAuditService().record(request, enabled ? AdminAuditAction.ENABLE_DEV_MODE : AdminAuditAction.DISABLE_DEV_MODE);
        return new ApiStandardResponse<>(EnvKit.isDevMode());
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
