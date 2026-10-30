package com.zrlog.admin.business.automation;

/** Durable identity and in-flight ownership are internal, separate from the editable plan. */
public final class StoredTask extends AutomationModels.Plan {
    public int ownerId;
    public int ownerAuthVersion;
    public String ownerRole;
    public long createdAt;
    public long updatedAt;
    public Long nextRunAt;
    public Long lastRunAt;
    public String lastStatus;
    public AutomationModels.Run activeRun;
    public long leaseUntil;
}
