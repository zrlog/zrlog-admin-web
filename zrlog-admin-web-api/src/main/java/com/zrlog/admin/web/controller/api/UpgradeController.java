package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.common.util.BeanUtil;
import com.zrlog.admin.business.automation.AutomaticUpgradeTaskProvider;
import com.zrlog.admin.business.automation.UpgradeExecutionCoordinator;
import com.zrlog.admin.business.rest.request.ExecuteUpgradeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.UpgradeCheckResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.DockerUpgradeProgressService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.service.UpgradeService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAction;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class UpgradeController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final DockerUpgradeProgressService dockerUpgradeProgressService;
    private final AdminAuditService adminAuditService;
    private final MessageCenterOperationService messageCenterOperationService;

    private final AdminPageDataFactory pages;

    private final UpgradeService upgrades;
    private final Supplier<UpdateVersionInfoPlugin> updatePlugin;
    private final Supplier<DockerControl> docker;
    private final UpgradeExecutionCoordinator executions;

    @Inject
    public UpgradeController(
            UpgradeService upgrades,
            Supplier<UpdateVersionInfoPlugin> updatePlugin,
            Supplier<DockerControl> docker,
            AdminPageDataFactory pages,
            DockerUpgradeProgressService dockerUpgradeProgressService,
            AdminAuditService adminAuditService,
            MessageCenterOperationService messageCenterOperationService,
            AdminRequestIdentity requestIdentity,
            UpgradeExecutionCoordinator executions) {
        this.requestIdentity = requestIdentity;
        this.dockerUpgradeProgressService = dockerUpgradeProgressService;
        this.adminAuditService = adminAuditService;
        this.messageCenterOperationService = messageCenterOperationService;

        this.pages = pages;
        this.upgrades = upgrades;
        this.updatePlugin = updatePlugin;
        this.docker = docker;
        this.executions = executions;
    }


    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeCheck")
    public AdminPageDataResponse<PreCheckVersionResponse> index() throws SQLException {
        // Keep the public check fresh; page rendering and task recovery can use cached metadata.
        boolean apiCheck = request.getUri().endsWith("/api" + Constants.ADMIN_URI_BASE_PATH + "/upgrade");
        boolean fetch = "true".equals(request.getParaToStr("fetch", apiCheck ? "true" : "false"));
        UpgradeCheckResponse preCheckVersionResponse = BeanUtil.convert(
                upgrades.preUpgradeVersion(fetch, updatePlugin.get()), UpgradeCheckResponse.class);
        preCheckVersionResponse.setExecution(executions.view(requestIdentity.current()));
        return pages.response(requestIdentity.optional(), preCheckVersionResponse,
                Objects.equals(preCheckVersionResponse.getUpgrade(), true) ? "" : I18nUtil.getAdminBackendStringFromRes("admin.upgrade.version.notFound"), request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeNotice")
    public ApiStandardResponse<CheckVersionResponse> notice() {
        boolean fetch = Objects.equals("true", request.getParaToStr("fetch", "false"));
        return new ApiStandardResponse<>(
                upgrades.getCheckVersionResponse(fetch, updatePlugin.get()),
                ""
        );
    }


    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public void doUpgrade() throws IOException, SQLException {
        ExecuteUpgradeRequest upgradeRequest = getRequestBodyWithNullCheck(ExecuteUpgradeRequest.class);
        try {
            executeUpgrade(upgradeRequest);
        } catch (UpgradeExecutionCoordinator.Conflict conflict) {
            // Keep a business rejection distinguishable from a stream interrupted by process replacement.
            var result = new ApiStandardResponse<>(conflict.execution);
            result.setError(409);
            result.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.automation.upgrade.pending"));
            response.renderJson(result);
        }
    }

    private void executeUpgrade(ExecuteUpgradeRequest upgradeRequest) throws IOException, SQLException {
        if (upgradeRequest.getDocker() != null) {
            DockerControl client = dockerClient();
            var dockerRequest = upgradeRequest.getDocker();
            Version target = new Version();
            target.setVersion(dockerRequest.version);
            target.setBuildId(dockerRequest.buildId);
            var receipt = executions.claimManual(requestIdentity.current(), target, dockerRequest.requestId,
                    dockerRequest.channel, upgradeRequest.getReviewedExecutionId());
            DockerUpdaterProtocol.Job job;
            boolean submitted = false;
            try {
                job = client.submit(dockerRequest);
                if (job == null || !Objects.equals(job.id, dockerRequest.requestId)) {
                    throw new IllegalStateException("Updater returned a different upgrade task");
                }
                submitted = true;
            } finally {
                executions.finish(receipt, submitted);
            }
            recordUpgradeAudit(upgradeRequest);
            if (isSseRequest()) {
                streamDockerUpgrade(client, job);
            } else {
                response.renderJson(new ApiStandardResponse<>(job));
            }
            return;
        }
        UpdateVersionInfoPlugin plugin = updatePlugin.get();
        Map<String, Object> backend = I18nUtil.getBackend();
        PreCheckVersionResponse check = upgrades.preUpgradeVersion(true, plugin);
        Version target = Boolean.TRUE.equals(check.getUpgrade()) ? check.getVersion() : null;
        // Manual instructions and no-change responses do not reserve a new upgrade execution.
        AutomaticUpgradeTaskProvider.Receipt receipt = target != null
                && Boolean.TRUE.equals(check.getOnlineUpgradable()) && !Boolean.TRUE.equals(check.getDockerMode())
                ? executions.claimManual(requestIdentity.current(), target, null, target.getChannel(),
                        upgradeRequest.getReviewedExecutionId()) : null;
        if (!isSseRequest()) {
            UpgradeProcessResponse upgradeProcessResponse;
            try {
                upgradeProcessResponse = executeClaimedUpgrade(target, receipt, UpgradeProgressListener.NONE, backend);
                this.messageCenterOperationService.recordUpgradeResult(upgradeProcessResponse);
            } catch (RuntimeException e) {
                this.messageCenterOperationService.recordUpgradeError(e.getMessage());
                throw e;
            }
            recordUpgradeAudit(upgradeRequest);
            response.renderJson(new ApiStandardResponse<>(
                    upgradeProcessResponse));
            return;
        }
        AdminSseEmitter.write(
                response,
                "admin-upgrade",
                "sse-error",
                emitter -> {
                    UpgradeProcessResponse upgradeProcessResponse;
                    try {
                        upgradeProcessResponse = executeClaimedUpgrade(target, receipt, emitter::send, backend);
                        this.messageCenterOperationService.recordUpgradeResult(
                                upgradeProcessResponse);
                    } catch (Exception e) {
                        this.messageCenterOperationService.recordUpgradeError(e.getMessage());
                        throw e;
                    }
                    recordUpgradeAudit(upgradeRequest);
                    emitter.send("response", new ApiStandardResponse<>(upgradeProcessResponse));
                });
    }

    private UpgradeProcessResponse executeClaimedUpgrade(Version target, AutomaticUpgradeTaskProvider.Receipt receipt,
                                                          UpgradeProgressListener listener, Map<String, Object> backend)
            throws SQLException {
        boolean submitted = false;
        try {
            UpgradeProcessResponse result = upgrades.doUpgradeVersion(target, listener, backend);
            submitted = Boolean.TRUE.equals(result.getFinish());
            return result;
        } finally {
            if (receipt != null) executions.finish(receipt, submitted);
        }
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public void dockerStatus() throws IOException {
        DockerControl client = dockerClient();
        DockerUpdaterProtocol.Job job;
        try {
            String requestId = request.getParaToStr("requestId");
            job = client.status(requestId);
            if (job != null && !Objects.equals(requestId, job.id)) {
                throw new IllegalStateException("Updater returned a different upgrade task");
            }
        } catch (DockerControl.RequestException error) {
            if (error.getStatus() != 404) {
                throw error;
            }
            job = null;
        }
        if (isSseRequest()) {
            streamDockerUpgrade(client, job);
        } else {
            response.renderJson(new ApiStandardResponse<>(job));
        }
    }

    private void streamDockerUpgrade(DockerControl client, DockerUpdaterProtocol.Job job) throws IOException {
        Map<String, Object> backend = I18nUtil.getBackend();
        AdminSseEmitter.write(
                response,
                "admin-upgrade",
                "sse-error",
                emitter -> {
                    ApiStandardResponse<UpgradeProcessResponse> result =
                            this.dockerUpgradeProgressService.watch(
                                    client, job, emitter::send, backend);
                    emitter.send("response", result);
                });
    }

    protected DockerControl dockerClient() {
        if (!ZrLogUtil.isDockerMode()) {
            throw new IllegalStateException("Docker updater requires Docker mode");
        }
        return docker.get();
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    private void recordUpgradeAudit(ExecuteUpgradeRequest requestBody) {
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                AdminAuditAction.EXECUTE_UPGRADE,
                requestBody.isUpgradeRiskAccepted() ? "upgradeRiskAccepted" : "");
    }

}
