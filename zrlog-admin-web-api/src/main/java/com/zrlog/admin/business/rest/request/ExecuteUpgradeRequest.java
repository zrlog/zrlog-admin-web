package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ExecuteUpgradeRequest implements Validator {

    private Boolean upgradeRiskAccepted;
    private com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest docker;
    public com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest getDocker() { return docker; }
    public void setDocker(com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest value) { docker = value; }

    @Override
    public void doValid() {
        if (!Boolean.TRUE.equals(upgradeRiskAccepted)) {
            throw new ArgsException("upgradeRiskAccepted");
        }
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
