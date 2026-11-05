package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ExecuteUpgradeRequest implements Validator {

    private Boolean upgradeRiskAccepted;
    private String reviewedExecutionId;
    private com.zrlog.updater.spi.UpdateCommand task;

    public com.zrlog.updater.spi.UpdateCommand getTask() {
        return task;
    }

    public void setTask(com.zrlog.updater.spi.UpdateCommand task) {
        this.task = task;
    }

    @Override
    public void doValid() {
        if (!Boolean.TRUE.equals(upgradeRiskAccepted)) {
            throw new ArgsException("upgradeRiskAccepted");
        }
        if (reviewedExecutionId != null
                && !reviewedExecutionId.matches("[0-9]{10}\\.[0-9]{20}\\.[a-f0-9-]{36}")) {
            throw new ArgsException("reviewedExecutionId");
        }
        if (task != null) task.validate();
    }

    public String getReviewedExecutionId() {
        return reviewedExecutionId;
    }

    public void setReviewedExecutionId(String value) {
        reviewedExecutionId = value;
    }

    public boolean isUpgradeRiskAccepted() {
        return Boolean.TRUE.equals(upgradeRiskAccepted);
    }

    public Boolean getUpgradeRiskAccepted() {
        return upgradeRiskAccepted;
    }

    public void setUpgradeRiskAccepted(Boolean upgradeRiskAccepted) {
        this.upgradeRiskAccepted = upgradeRiskAccepted;
    }
}
