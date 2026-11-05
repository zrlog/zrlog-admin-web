package com.zrlog.admin.business.rest.request;

import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.exception.ArgsException;

/** Only edited source selections are sent; null preserves the updater setting. */
public class UpgradeSettingsRequest extends UpgradeWebSiteInfo {
    private String updateSource;

    public String getUpdateSource() {
        return updateSource;
    }

    public void setUpdateSource(String value) {
        updateSource = value;
    }

    @Override
    public void doValid() {
        super.doValid();
        if (updateSource != null && updateSource.length() > 255) {
            throw new ArgsException("updateSource");
        }
    }
}
