package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.automation.UpgradeExecutionCoordinator;
import com.zrlog.business.rest.response.PreCheckVersionResponse;

/** Upgrade readiness together with the shared manual/assistant execution receipt. */
public final class UpgradeCheckResponse extends PreCheckVersionResponse {
    private UpgradeExecutionCoordinator.View execution;

    public UpgradeExecutionCoordinator.View getExecution() { return execution; }

    public void setExecution(UpgradeExecutionCoordinator.View execution) { this.execution = execution; }
}
