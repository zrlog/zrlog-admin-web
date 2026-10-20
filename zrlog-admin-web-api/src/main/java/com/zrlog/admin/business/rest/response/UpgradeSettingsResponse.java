package com.zrlog.admin.business.rest.response;

import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

/**
 * Initial page data includes the runtime mode even when the updater is unavailable.
 */
public class UpgradeSettingsResponse extends UpgradeWebSiteInfo {
    private final boolean dockerMode;
    private DockerUpdaterProtocol.SettingsResponse dockerSettings;
    private String dockerErrorCode;

    public UpgradeSettingsResponse(UpgradeWebSiteInfo settings, boolean dockerMode) {
        setAutoUpgradeVersion(settings.getAutoUpgradeVersion());
        setUpgradePreview(settings.getUpgradePreview());
        if (!dockerMode) {
            setUpgradePackageSource(settings.getUpgradePackageSource());
        }
        this.dockerMode = dockerMode;
    }

    public boolean isDockerMode() {
        return dockerMode;
    }

    public DockerUpdaterProtocol.SettingsResponse getDockerSettings() {
        return dockerSettings;
    }

    public void setDockerSettings(DockerUpdaterProtocol.SettingsResponse value) {
        dockerSettings = value;
    }

    public String getDockerErrorCode() {
        return dockerErrorCode;
    }

    public void setDockerErrorCode(String value) {
        dockerErrorCode = value;
    }
}
