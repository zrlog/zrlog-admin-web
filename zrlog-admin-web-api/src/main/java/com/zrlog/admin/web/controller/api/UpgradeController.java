package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.HttpMethod;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.util.ZrLogUtil;
import com.zrlog.admin.business.rest.request.ExecuteUpgradeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminStaticService;
import com.zrlog.admin.business.service.DockerUpgradeProgressService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminSseEmitter;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.util.I18nUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class UpgradeController extends BaseController {


    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeCheck")
    public AdminPageDataResponse<PreCheckVersionResponse> index() {
        // Keep the public check fresh; page rendering and task recovery can use cached metadata.
        boolean apiCheck = request.getUri().endsWith("/api" + Constants.ADMIN_URI_BASE_PATH + "/upgrade");
        boolean fetch = "true".equals(request.getParaToStr("fetch", apiCheck ? "true" : "false"));
        PreCheckVersionResponse preCheckVersionResponse = AdminStaticService.getInstance().getUpgradeService().preUpgradeVersion(fetch, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class));
        return new AdminPageDataResponse<>(preCheckVersionResponse,
                Objects.equals(preCheckVersionResponse.getUpgrade(), true) ? "" : I18nUtil.getAdminBackendStringFromRes("admin.upgrade.version.notFound"), request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeNotice")
    public ApiStandardResponse<CheckVersionResponse> notice() {
        boolean fetch = Objects.equals("true", request.getParaToStr("fetch", "false"));
        return new ApiStandardResponse<>(
                AdminStaticService.getInstance().getUpgradeService().getCheckVersionResponse(fetch, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class)),
                ""
        );
    }


    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public void doUpgrade() throws IOException {
        ExecuteUpgradeRequest upgradeRequest = getRequestBodyWithNullCheck(ExecuteUpgradeRequest.class);
        if (upgradeRequest.getDocker() != null) {
            DockerControl client = dockerClient();
            DockerUpdaterProtocol.Job job = client.submit(upgradeRequest.getDocker());
            recordUpgradeAudit(upgradeRequest);
            if (isSseRequest()) {
                streamDockerUpgrade(client, job);
            } else {
                response.renderJson(new ApiStandardResponse<>(job));
            }
            return;
        }
        UpdateVersionInfoPlugin plugin = Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class);
        Map<String, Object> backend = I18nUtil.getBackend();
        if (!isSseRequest()) {
            UpgradeProcessResponse upgradeProcessResponse;
            try {
                upgradeProcessResponse = AdminStaticService.getInstance().getUpgradeService()
                        .doUpgrade(plugin, UpgradeProgressListener.NONE, backend);
                new MessageCenterOperationService().recordUpgradeResult(upgradeProcessResponse);
            } catch (RuntimeException e) {
                new MessageCenterOperationService().recordUpgradeError(e.getMessage());
                throw e;
            }
            recordUpgradeAudit(upgradeRequest);
            response.renderJson(new ApiStandardResponse<>(
                    upgradeProcessResponse));
            return;
        }
        AdminSseEmitter.write(response, "admin-upgrade", "sse-error", emitter -> {
            UpgradeProcessResponse upgradeProcessResponse;
            try {
                upgradeProcessResponse = AdminStaticService.getInstance().getUpgradeService()
                        .doUpgrade(plugin, emitter::send, backend);
                new MessageCenterOperationService().recordUpgradeResult(upgradeProcessResponse);
            } catch (Exception e) {
                new MessageCenterOperationService().recordUpgradeError(e.getMessage());
                throw e;
            }
            recordUpgradeAudit(upgradeRequest);
            emitter.send("response", new ApiStandardResponse<>(upgradeProcessResponse));
        });
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
            if (error.getStatus() != 404) { throw error; }
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
        AdminSseEmitter.write(response, "admin-upgrade", "sse-error", emitter -> {
            ApiStandardResponse<UpgradeProcessResponse> result = new DockerUpgradeProgressService()
                    .watch(client, job, emitter::send, backend);
            emitter.send("response", result);
        });
    }

    protected DockerControl dockerClient() {
        if (!ZrLogUtil.isDockerMode()) { throw new IllegalStateException("Docker updater requires Docker mode"); }
        return com.zrlog.common.updater.UpdaterHosts.dockerControl();
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    private void recordUpgradeAudit(ExecuteUpgradeRequest requestBody) {
        new AdminAuditService().record(request, AdminAuditAction.EXECUTE_UPGRADE,
                requestBody.isUpgradeRiskAccepted() ? "upgradeRiskAccepted" : "");
    }

}
