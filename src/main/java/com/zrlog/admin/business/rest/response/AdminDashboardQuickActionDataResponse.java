package com.zrlog.admin.business.rest.response;

public class AdminDashboardQuickActionDataResponse {

    private Long draftCount;

    public AdminDashboardQuickActionDataResponse() {
    }

    public AdminDashboardQuickActionDataResponse(Long draftCount) {
        this.draftCount = draftCount;
    }

    public Long getDraftCount() {
        return draftCount;
    }

    public void setDraftCount(Long draftCount) {
        this.draftCount = draftCount;
    }
}
