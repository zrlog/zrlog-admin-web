package com.zrlog.admin.business.rest.response;


import com.zrlog.common.vo.LockVO;

import java.util.List;

public class DevInfoResponse {

    private List<LockVO> locks;
    private List<WebsiteKvEntryResponse> cacheEntries;
    private boolean devMode;

    public List<LockVO> getLocks() {
        return locks;
    }

    public void setLocks(List<LockVO> locks) {
        this.locks = locks;
    }

    public List<WebsiteKvEntryResponse> getCacheEntries() {
        return cacheEntries;
    }

    public void setCacheEntries(List<WebsiteKvEntryResponse> cacheEntries) {
        this.cacheEntries = cacheEntries;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }
}
