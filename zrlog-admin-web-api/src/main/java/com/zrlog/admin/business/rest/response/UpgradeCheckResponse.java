package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.automation.UpgradeExecutionCoordinator;
import com.zrlog.business.rest.response.PreCheckVersionResponse;

/** Upgrade readiness together with the shared manual/assistant execution receipt. */
public final class UpgradeCheckResponse extends PreCheckVersionResponse {
    private UpgradeExecutionCoordinator.View execution;
    private String backupProtectionMessage;
    private String backupProtectionRecommendation;

    public String getBackupProtectionMessage() {
        return backupProtectionMessage;
    }

    public void setBackupProtectionMessage(String message) {
        this.backupProtectionMessage = message;
    }

    public String getBackupProtectionRecommendation() {
        return backupProtectionRecommendation;
    }

    public void setBackupProtectionRecommendation(String recommendation) {
        this.backupProtectionRecommendation = recommendation;
    }

    public UpgradeExecutionCoordinator.View getExecution() {
        return execution;
    }

    public void setExecution(UpgradeExecutionCoordinator.View execution) {
        this.execution = execution;
    }
}
