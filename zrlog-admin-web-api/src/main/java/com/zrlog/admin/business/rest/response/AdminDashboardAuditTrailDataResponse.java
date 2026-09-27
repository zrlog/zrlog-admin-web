package com.zrlog.admin.business.rest.response;

import java.util.List;

public class AdminDashboardAuditTrailDataResponse {

    private List<AdminAuditLogEntryResponse> auditLogs;
    private Boolean loading;

    public AdminDashboardAuditTrailDataResponse() {
    }

    public AdminDashboardAuditTrailDataResponse(List<AdminAuditLogEntryResponse> auditLogs, Boolean loading) {
        this.auditLogs = auditLogs;
        this.loading = loading;
    }

    public List<AdminAuditLogEntryResponse> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<AdminAuditLogEntryResponse> auditLogs) {
        this.auditLogs = auditLogs;
    }

    public Boolean getLoading() {
        return loading;
    }

    public void setLoading(Boolean loading) {
        this.loading = loading;
    }
}
