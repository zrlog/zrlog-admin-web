package com.zrlog.admin.business.rest.response;


import com.zrlog.common.vo.LockVO;

import java.util.List;

public class DevInfoResponse {

    private List<LockVO> locks;

    public List<LockVO> getLocks() {
        return locks;
    }

    public void setLocks(List<LockVO> locks) {
        this.locks = locks;
    }
}
