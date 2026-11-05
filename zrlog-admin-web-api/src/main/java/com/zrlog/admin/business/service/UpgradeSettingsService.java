package com.zrlog.admin.business.service;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.business.rest.response.UpgradeSettingsResponse;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.updater.spi.UpdateOperations;
import com.zrlog.updater.spi.UpdateSourceSettings;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Aggregates the existing website settings and the public updater source settings. */
@Singleton
public class UpgradeSettingsService {
    private final WebSiteService webSiteService;

    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;

    private final WebSiteSettingsService settingsService;
    private final Supplier<UpdateOperations> operations;

    @Inject
    public UpgradeSettingsService(
            WebSiteSettingsService settingsService,
            Supplier<UpdateOperations> operations,
            AccountPermissionService permissionService,
            AccountIdentityService identities,
            WebSiteService webSiteService) {
        this.webSiteService = webSiteService;

        this.permissionService = permissionService;
        this.identities = identities;

        this.operations = operations;
        this.settingsService = settingsService;
    }

    public UpgradeSettingsResponse read(AccountAccess actor, String requestedChannel)
            throws SQLException {
        actor = authorize(actor);
        UpgradeSettingsResponse response =
                new UpgradeSettingsResponse(webSiteService.upgradeWebSiteInfo());
        response.setScheduleManaged(settingsService.isUpgradeScheduleManaged());
        String channel =
                requestedChannel == null ? channel(response.getUpgradePreview()) : requestedChannel;
        var selected = operations.get();
        if (selected.managesSourceSettings()) requireSourcePermission(actor);
        UpdateSourceSettings sources =
                selected.settings(channel, UpdaterHosts.text(I18nUtil.getBackend()));
        if (sources.managedByUpdater) response.setUpgradePackageSource(null);
        response.setSourceSettings(sources);
        return response;
    }

    public void save(AccountAccess actor, UpgradeSettingsRequest input, AdminAuditContext audit)
            throws SQLException {
        actor = authorize(actor);
        input.doValid();
        if (settingsService.isUpgradeScheduleManaged()
                && !java.util.Objects.equals(
                        input.getAutoUpgradeVersion(),
                        webSiteService.upgradeWebSiteInfo().getAutoUpgradeVersion())) {
            throw new ArgsException("autoUpgradeVersion");
        }
        String channel = channel(input.getUpgradePreview());
        var operations = this.operations.get();
        boolean managed = operations.managesSourceSettings();
        if (managed) requireSourcePermission(actor);
        UpgradeWebSiteInfo settings = new UpgradeWebSiteInfo();
        settings.setAutoUpgradeVersion(input.getAutoUpgradeVersion());
        settings.setUpgradePreview(input.getUpgradePreview());
        String source = input.getUpdateSource();
        if (source == null && !managed) source = input.getUpgradePackageSource();
        settings.setUpgradePackageSource(
                managed
                        ? null
                        : source == null
                                ? webSiteService.upgradeWebSiteInfo().getUpgradePackageSource()
                                : source);
        settingsService.validateUpgrade(settings);
        if (source == null) {
            identities.revalidate(actor);
            settingsService.updateUpgrade(settings, audit);
            return;
        }
        UpdateSourceSettings previous =
                operations.settings(channel, UpdaterHosts.text(I18nUtil.getBackend()));
        identities.revalidate(actor);
        if (previous.errorCode != null)
            throw new com.zrlog.updater.spi.UpdateOperationException(
                    503, previous.errorCode, previous.errorMessage);
        identities.revalidate(actor);
        operations.saveSettings(source, channel, UpdaterHosts.text(I18nUtil.getBackend()));
        try {
            identities.revalidate(actor);
            settingsService.updateUpgrade(settings, audit);
        } catch (SQLException | RuntimeException error) {
            if (previous.managedByUpdater) {
                try {
                    operations.restoreSettings(previous, UpdaterHosts.text(I18nUtil.getBackend()));
                } catch (RuntimeException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            throw error;
        }
    }

    private AccountAccess authorize(AccountAccess actor) {
        actor = identities.revalidate(actor);
        permissionService.require(actor, AccountAction.SITE_CONFIGURE);
        return actor;
    }

    private void requireSourcePermission(AccountAccess actor) {
        permissionService.require(actor, AccountAction.SYSTEM_MANAGE);
    }

    private String channel(Boolean preview) {
        return Boolean.TRUE.equals(preview) ? "preview" : "release";
    }
}
