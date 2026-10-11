package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

public class DockerUpgradeSettingsRequest extends DockerUpdaterProtocol.Settings implements Validator {
    @Override
    public void doValid() {
        if (repository == null || repository.length() > 255 || !("release".equals(channel) || "preview".equals(channel))) {
            throw new ArgsException("dockerRepository");
        }
    }
}
