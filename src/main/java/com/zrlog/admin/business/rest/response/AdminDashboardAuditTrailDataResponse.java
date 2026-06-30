package com.zrlog.admin.business.rest.response;

import java.util.List;
import java.util.Map;

public class AdminDashboardAuditTrailDataResponse {

    private List<Map<String, Object>> auditLogs;
    private Boolean loading;

    public AdminDashboardAuditTrailDataResponse() {
    }

    public AdminDashboardAuditTrailDataResponse(List<Map<String, Object>> auditLogs, Boolean loading) {
        this.auditLogs = auditLogs;
        this.loading = loading;
    }

    public List<Map<String, Object>> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<Map<String, Object>> auditLogs) {
        this.auditLogs = auditLogs;
    }

    public Boolean getLoading() {
        return loading;
    }

    public void setLoading(Boolean loading) {
        this.loading = loading;
    }
}
