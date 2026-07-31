package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;

public class ExecuteUpgradeRequest implements Validator {

    private Boolean backupRiskAccepted;

    @Override
    public void doValid() {
    }

    public boolean isBackupRiskAccepted() {
        return Boolean.TRUE.equals(backupRiskAccepted);
    }

    public Boolean getBackupRiskAccepted() {
        return backupRiskAccepted;
    }

    public void setBackupRiskAccepted(Boolean backupRiskAccepted) {
        this.backupRiskAccepted = backupRiskAccepted;
    }
}
