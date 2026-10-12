package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.HttpMethod;
import com.zrlog.common.updater.docker.DockerUpdaterClient;
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
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeCheck")
    public AdminPageDataResponse<PreCheckVersionResponse> index() {
        // Page rendering and durable Docker job recovery must not depend on the metadata server.
        boolean fetch = "true".equals(request.getParaToStr("fetch", "false"));
        PreCheckVersionResponse preCheckVersionResponse = AdminStaticService.getInstance().getUpgradeService().preUpgradeVersion(fetch, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class));
        return new AdminPageDataResponse<>(preCheckVersionResponse,
                Objects.equals(preCheckVersionResponse.getUpgrade(), true) ? "" : I18nUtil.getAdminBackendStringFromRes("admin.upgrade.version.notFound"), request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeNotice")
    public ApiStandardResponse<CheckVersionResponse> notice() {
        boolean fetch = Objects.equals("true", request.getParaToStr("fetch", "false"));
        return new ApiStandardResponse<>(
                AdminStaticService.getInstance().getUpgradeService().getCheckVersionResponse(fetch, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class)),
                ""
        );
    }


    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    @RequestMethod(method = HttpMethod.POST)
    public void doUpgrade() throws IOException {
        ExecuteUpgradeRequest upgradeRequest = getRequestBodyWithNullCheck(ExecuteUpgradeRequest.class);
        if (upgradeRequest.getDocker() != null) {
            DockerUpdaterClient client = dockerClient();
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
        DockerUpdaterClient client = dockerClient();
        DockerUpdaterProtocol.Job job;
        try {
            String requestId = request.getParaToStr("requestId");
            job = client.status(requestId);
            if (job != null && !Objects.equals(requestId, job.id)) {
                throw new IllegalStateException("Updater returned a different upgrade task");
            }
        } catch (DockerUpdaterClient.RequestException error) {
            if (error.getStatus() != 404) { throw error; }
            job = null;
        }
        if (isSseRequest()) {
            streamDockerUpgrade(client, job);
        } else {
            response.renderJson(new ApiStandardResponse<>(job));
        }
    }

    private void streamDockerUpgrade(DockerUpdaterClient client, DockerUpdaterProtocol.Job job) throws IOException {
        Map<String, Object> backend = I18nUtil.getAdminBackend();
        AdminSseEmitter.write(response, "admin-upgrade", "sse-error", emitter -> {
            ApiStandardResponse<UpgradeProcessResponse> result = new DockerUpgradeProgressService()
                    .watch(client, job, emitter::send, backend);
            emitter.send("response", result);
        });
    }

    protected DockerUpdaterClient dockerClient() {
        if (!ZrLogUtil.isDockerMode()) { throw new IllegalStateException("Docker updater requires Docker mode"); }
        return DockerUpdaterClient.fromEnvironment();
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
