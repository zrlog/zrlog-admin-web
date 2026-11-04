package com.zrlog.admin.business.rest.response;

import com.zrlog.common.updater.docker.DockerUpdaterProtocol;

/** The updater snapshot with localized public messages, excluding raw updater diagnostics. */
public class DockerUpgradeJobResponse extends DockerUpdaterProtocol.Job {
    public String detail;
}
