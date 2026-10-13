package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.business.rest.response.UpgradeSettingsResponse;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.updater.docker.DockerUpdaterClient;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;

import java.sql.SQLException;

/** Aggregates the existing website settings and the private updater protocol. */
public class UpgradeSettingsService {
    private final WebSiteSettingsService settingsService;

    public UpgradeSettingsService() { this(new WebSiteSettingsService()); }
    public UpgradeSettingsService(WebSiteSettingsService settingsService) { this.settingsService = settingsService; }

    public UpgradeSettingsResponse read(String requestedChannel) throws SQLException {
        UpgradeSettingsResponse response = new UpgradeSettingsResponse(new WebSiteService().upgradeWebSiteInfo(), isDockerMode());
        if (!response.isDockerMode()) { return response; }
        requireDockerPermission();
        String channel = requestedChannel == null ? channel(response.getUpgradePreview()) : requestedChannel;
        if (!"preview".equals(channel) && !"release".equals(channel)) { throw new ArgsException("channel"); }
        try {
            response.setDockerSettings(dockerClient().settings(channel));
        } catch (DockerUpdaterClient.RequestException error) {
            response.setDockerErrorCode(error.getCode());
        } catch (IllegalArgumentException | IllegalStateException error) {
            response.setDockerErrorCode("updater_not_configured");
        }
        return response;
    }

    public void save(UpgradeSettingsRequest input, HttpRequest request) throws SQLException {
        input.doValid();
        boolean docker = isDockerMode();
        if (docker) { requireDockerPermission(); }
        if (!docker && input.getDockerRepository() != null) { throw new ArgsException("dockerRepository"); }
        // Persist only the website fields. Never pass response/runtime fields to the generic KV writer.
        UpgradeWebSiteInfo settings = new UpgradeWebSiteInfo();
        settings.setAutoUpgradeVersion(input.getAutoUpgradeVersion());
        settings.setUpgradePreview(input.getUpgradePreview());
        settings.setUpgradePackageSource(docker ? null : input.getUpgradePackageSource());
        settingsService.validateUpgrade(settings);
        if (input.getDockerRepository() == null) {
            settingsService.updateUpgrade(settings, request);
            return;
        }
        DockerUpdaterClient client = dockerClient();
        String channel = channel(input.getUpgradePreview());
        DockerUpdaterProtocol.SettingsResponse previous = client.settings(channel);
        String repository = input.getDockerRepository();
        if (!repository.isEmpty() && previous.images.stream().noneMatch(image -> repository.equals(image.repository))) {
            throw new ArgsException("dockerRepository");
        }
        client.saveSettings(repository, channel);
        try {
            settingsService.updateUpgrade(settings, request);
        } catch (SQLException | RuntimeException error) {
            try {
                client.saveSettings(previous.settings.repository, previous.settings.channel);
            } catch (RuntimeException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    protected boolean isDockerMode() { return ZrLogUtil.isDockerMode(); }
    protected DockerUpdaterClient dockerClient() { return DockerUpdaterClient.fromEnvironment(); }
    protected void requireDockerPermission() { AccountPermissionService.require(AccountAction.SYSTEM_MANAGE); }
    private String channel(Boolean preview) { return Boolean.TRUE.equals(preview) ? "preview" : "release"; }
}
