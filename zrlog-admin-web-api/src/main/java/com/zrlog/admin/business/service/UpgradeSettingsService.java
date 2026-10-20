package com.zrlog.admin.business.service;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.business.rest.response.UpgradeSettingsResponse;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Aggregates the existing website settings and the private updater protocol.
 */
@Singleton
public class UpgradeSettingsService {
    private final WebSiteService webSiteService;

    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;

    private final WebSiteSettingsService settingsService;
    private final Supplier<DockerControl> docker;

    @Inject
    public UpgradeSettingsService(
            WebSiteSettingsService settingsService,
            Supplier<DockerControl> docker,
            AccountPermissionService permissionService,
            AccountIdentityService identities,
            WebSiteService webSiteService) {
        this.webSiteService = webSiteService;

        this.permissionService = permissionService;
        this.identities = identities;

        this.docker = docker;
        this.settingsService = settingsService;
    }

    public UpgradeSettingsResponse read(AccountAccess actor, String requestedChannel) throws SQLException {
        actor = authorize(actor);
        UpgradeSettingsResponse response =
                new UpgradeSettingsResponse(
                        this.webSiteService.upgradeWebSiteInfo(), isDockerMode());
        if (!response.isDockerMode()) {
            return response;
        }
        requireDockerPermission(actor);
        String channel = requestedChannel == null ? channel(response.getUpgradePreview()) : requestedChannel;
        if (!"preview".equals(channel) && !"release".equals(channel)) {
            throw new ArgsException("channel");
        }
        try {
            response.setDockerSettings(dockerClient().settings(channel));
        } catch (DockerControl.RequestException error) {
            response.setDockerErrorCode(error.getCode());
        } catch (IllegalArgumentException | IllegalStateException error) {
            response.setDockerErrorCode("updater_not_configured");
        }
        return response;
    }

    public void save(AccountAccess actor, UpgradeSettingsRequest input, AdminAuditContext audit) throws SQLException {
        actor = authorize(actor);
        input.doValid();
        boolean docker = isDockerMode();
        if (docker) {
            requireDockerPermission(actor);
        }
        if (!docker && input.getDockerRepository() != null) {
            throw new ArgsException("dockerRepository");
        }
        // Persist only the website fields. Never pass response/runtime fields to the generic KV writer.
        UpgradeWebSiteInfo settings = new UpgradeWebSiteInfo();
        settings.setAutoUpgradeVersion(input.getAutoUpgradeVersion());
        settings.setUpgradePreview(input.getUpgradePreview());
        settings.setUpgradePackageSource(docker ? null : input.getUpgradePackageSource());
        settingsService.validateUpgrade(settings);
        if (input.getDockerRepository() == null) {
            identities.revalidate(actor);
            settingsService.updateUpgrade(settings, audit);
            return;
        }
        DockerControl client = dockerClient();
        String channel = channel(input.getUpgradePreview());
        DockerUpdaterProtocol.SettingsResponse previous = client.settings(channel);
        String repository = input.getDockerRepository();
        if (!repository.isEmpty() && previous.images.stream().noneMatch(image -> repository.equals(image.repository))) {
            throw new ArgsException("dockerRepository");
        }
        identities.revalidate(actor);
        client.saveSettings(repository, channel);
        try {
            identities.revalidate(actor);
            settingsService.updateUpgrade(settings, audit);
        } catch (SQLException | RuntimeException error) {
            try {
                client.saveSettings(previous.settings.repository, previous.settings.channel);
            } catch (RuntimeException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    protected boolean isDockerMode() {
        return ZrLogUtil.isDockerMode();
    }

    protected DockerControl dockerClient() {
        return docker.get();
    }

    private AccountAccess authorize(AccountAccess actor) {
        actor = identities.revalidate(actor);
        permissionService.require(actor, AccountAction.SITE_CONFIGURE);
        return actor;
    }

    private void requireDockerPermission(AccountAccess actor) {
        permissionService.require(actor, AccountAction.SYSTEM_MANAGE);
    }

    private String channel(Boolean preview) {
        return Boolean.TRUE.equals(preview) ? "preview" : "release";
    }
}
