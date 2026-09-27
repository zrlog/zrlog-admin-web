package com.zrlog.admin.business.rest.response;

public class IndexResponse {

    private final AdminDashboardConfigResponse dashboardConfig;
    private final FirstUseChecklistResponse firstUseChecklist;

    public IndexResponse(AdminDashboardConfigResponse dashboardConfig, FirstUseChecklistResponse firstUseChecklist) {
        this.dashboardConfig = dashboardConfig;
        this.firstUseChecklist = firstUseChecklist;
    }

    public AdminDashboardConfigResponse getDashboardConfig() {
        return dashboardConfig;
    }

    public FirstUseChecklistResponse getFirstUseChecklist() {
        return firstUseChecklist;
    }
}
