package com.zrlog.admin.business.service;

import com.zrlog.admin.business.automation.AutomaticUpgradeTaskProvider;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.service.UpgradeService;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Keeps deployment-specific execution in the existing updater; scheduling adds policy and audit only. */
public final class HostAutomaticUpgradeOperations implements AutomaticUpgradeTaskProvider.Operations {
    private final UpgradeService upgrades;
    private final WebsiteKvService settings;
    private final Supplier<UpdateVersionInfoPlugin> updates;
    private final Supplier<Updater> updater;
    private final BooleanSupplier faas;
    private final AdminAuditService audit;

    public HostAutomaticUpgradeOperations(UpgradeService upgrades, WebsiteKvService settings,
                                          Supplier<UpdateVersionInfoPlugin> updates, Supplier<Updater> updater,
                                          BooleanSupplier faas, AdminAuditService audit) {
        this.upgrades = upgrades;
        this.settings = settings;
        this.updates = updates;
        this.updater = updater;
        this.faas = faas;
        this.audit = audit;
    }

    @Override
    public boolean available() {
        // The existing archive updater owns asynchronous transfer/restart workers. Do not launch them on FaaS.
        return !faas.getAsBoolean() && updater.get() != null && updates.get() != null;
    }

    @Override
    public String channel() { return Boolean.TRUE.equals(settings.upgradeWebSiteInfo().getUpgradePreview()) ? "preview" : "release"; }

    @Override
    public String currentBuildId() { return BlogBuildInfoUtil.getBuildId(); }

    @Override
    public PreCheckVersionResponse check() { return upgrades.preUpgradeVersion(true, updates.get()); }

    @Override
    public boolean submit(Version target, TaskExecutionContext context) throws Exception {
        context.checkActive();
        audit.record(context.audit(), AdminAuditAction.EXECUTE_UPGRADE, "assistant:" + context.runId());
        if (updater.get().getType() == UpdaterTypeEnum.DOCKER) {
            var request = new DockerUpdaterProtocol.UpgradeRequest();
            request.requestId = "assistant_" + context.runId().replace('.', '_');
            request.channel = channel();
            request.version = target.getArtifactVersion();
            request.buildId = target.getBuildId();
            var job = UpdaterHosts.dockerControl(updater.get()).submit(request);
            return job != null && request.requestId.equals(job.id) && !job.recoveryRequired
                    && !"failed".equals(job.phase) && !"interrupted".equals(job.phase);
        }
        return Boolean.TRUE.equals(upgrades.doUpgrade(target, updater.get(),
                (event, data) -> context.checkActive(), I18nUtil.getBackend()).getFinish());
    }
}
