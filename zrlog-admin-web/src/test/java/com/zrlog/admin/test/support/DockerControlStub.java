package com.zrlog.admin.test.support;

import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.spi.DockerControl;

/**
 * Tests must explicitly supply the operations used by a controller or SSE subscription.
 */
public abstract class DockerControlStub implements DockerControl {
    public DockerUpdaterProtocol.Capabilities capabilities() {
        throw new AssertionError("Unexpected capabilities request");
    }

    public DockerUpdaterProtocol.SettingsResponse settings(String channel) {
        throw new AssertionError("Unexpected settings request");
    }

    public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
        throw new AssertionError("Unexpected settings write");
    }

    public DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request) {
        throw new AssertionError("Unexpected upgrade submission");
    }

    public DockerUpdaterProtocol.Job status(String id) {
        throw new AssertionError("Unexpected status request");
    }
}
