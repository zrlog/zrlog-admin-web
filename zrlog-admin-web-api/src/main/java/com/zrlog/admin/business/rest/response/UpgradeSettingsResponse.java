package com.zrlog.admin.business.rest.response;

import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.updater.spi.UpdateSourceSettings;

public class UpgradeSettingsResponse extends UpgradeWebSiteInfo {
    private UpdateSourceSettings sourceSettings;
    private boolean scheduleManaged;

    public UpgradeSettingsResponse(UpgradeWebSiteInfo settings) {
        setAutoUpgradeVersion(settings.getAutoUpgradeVersion());
        setUpgradePreview(settings.getUpgradePreview());
        setUpgradePackageSource(settings.getUpgradePackageSource());
    }

    public boolean isScheduleManaged() {
        return scheduleManaged;
    }

    public void setScheduleManaged(boolean value) {
        scheduleManaged = value;
    }

    public UpdateSourceSettings getSourceSettings() {
        return sourceSettings;
    }

    public void setSourceSettings(UpdateSourceSettings value) {
        sourceSettings = value;
    }
}
