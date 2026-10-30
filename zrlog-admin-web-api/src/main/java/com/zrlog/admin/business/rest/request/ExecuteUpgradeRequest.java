package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ExecuteUpgradeRequest implements Validator {

    private Boolean upgradeRiskAccepted;
    private String reviewedExecutionId;
    private com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest docker;

    public com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest getDocker() {
        return docker;
    }

    public void setDocker(com.zrlog.common.updater.docker.DockerUpdaterProtocol.UpgradeRequest value) {
        docker = value;
    }

    @Override
    public void doValid() {
        if (!Boolean.TRUE.equals(upgradeRiskAccepted)) {
            throw new ArgsException("upgradeRiskAccepted");
        }
        if (reviewedExecutionId != null && !reviewedExecutionId.matches("[0-9]{10}\\.[0-9]{20}\\.[a-f0-9-]{36}")) {
            throw new ArgsException("reviewedExecutionId");
        }
        if (docker != null) {
            com.zrlog.updater.spi.DockerControl.requestId(docker.requestId);
            if (!("release".equals(docker.channel) || "preview".equals(docker.channel))
                    || docker.version == null || docker.version.isBlank() || docker.version.length() > 200
                    || docker.buildId == null || docker.buildId.isBlank() || docker.buildId.length() > 200) {
                throw new ArgsException("docker");
            }
        }
    }

    public String getReviewedExecutionId() { return reviewedExecutionId; }

    public void setReviewedExecutionId(String value) { reviewedExecutionId = value; }

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
