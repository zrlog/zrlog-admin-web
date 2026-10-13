package com.zrlog.admin.business.rest.request;

import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.exception.ArgsException;

/** Only edited repository selections are sent; null preserves the updater setting. */
public class UpgradeSettingsRequest extends UpgradeWebSiteInfo {
    private String dockerRepository;

    public String getDockerRepository() { return dockerRepository; }
    public void setDockerRepository(String value) { dockerRepository = value; }

    @Override
    public void doValid() {
        super.doValid();
        if (dockerRepository != null && dockerRepository.length() > 255) {
            throw new ArgsException("dockerRepository");
        }
    }
}
