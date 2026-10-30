package com.zrlog.admin.business.automation;

/** Durable identity and in-flight ownership are internal, separate from the editable plan. */
public final class StoredTask extends AutomationModels.Plan {
    /** Zero denotes a site task; positive IDs denote personal tasks. */
    public int ownerId;
    public Integer updatedBy;
    /** Server-derived ceiling; never accepted from the editable plan. */
    public java.util.List<String> executionActions = new java.util.ArrayList<>();
    public long createdAt;
    public long updatedAt;
    public Long nextRunAt;
    public Long lastRunAt;
    public String lastStatus;
    public AutomationModels.Run activeRun;
    public long leaseUntil;
}
