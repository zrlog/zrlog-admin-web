package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.DevInfoResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.util.DevKit;
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
        return new UpdateRecordResponse(true);
    }

    @ResponseBody
    @RequestMethod
    public AdminApiPageDataStandardResponse<DevInfoResponse> index() throws Exception {
        DevInfoResponse devInfoResponse = new DevInfoResponse();
        devInfoResponse.setLocks(DistributedLockManager.getInstance().getLocks());
        return new AdminApiPageDataStandardResponse<>(devInfoResponse,"",getRequest().getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<Void> enable() {
        if (!request.getServerConfig().isNativeImageAgent()) {
            System.getProperties().put("sws.run.mode", "dev");
        }
        DevKit.configDev(request.getServerConfig());
        return new AdminApiPageDataStandardResponse<>();
    }
}


