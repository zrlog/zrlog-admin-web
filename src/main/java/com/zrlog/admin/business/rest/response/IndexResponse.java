package com.zrlog.admin.business.rest.response;

public class IndexResponse {

    private final AdminDashboardConfigResponse dashboardConfig;

    public IndexResponse(AdminDashboardConfigResponse dashboardConfig) {
        this.dashboardConfig = dashboardConfig;
    }

    public AdminDashboardConfigResponse getDashboardConfig() {
        return dashboardConfig;
    }
}
