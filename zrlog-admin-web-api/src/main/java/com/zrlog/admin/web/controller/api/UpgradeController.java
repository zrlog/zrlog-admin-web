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
import com.zrlog.admin.business.rest.response.DockerRepositorySettingsResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminStaticService;
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
            DockerUpdaterProtocol.Job job = dockerClient().submit(upgradeRequest.getDocker());
            recordUpgradeAudit(upgradeRequest);
            response.renderJson(new ApiStandardResponse<>(job));
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
    public ApiStandardResponse<DockerUpdaterProtocol.Job> dockerStatus() {
        try {
            return new ApiStandardResponse<>(dockerClient().status(request.getParaToStr("requestId")));
        } catch (DockerUpdaterClient.RequestException error) {
            if (error.getStatus() == 404) { return new ApiStandardResponse<>(null); }
            throw error;
        }
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public DockerRepositorySettingsResponse dockerSettings() {
        try {
            return new DockerRepositorySettingsResponse(dockerClient().settings(request.getParaToStr("channel", "release")));
        } catch (DockerUpdaterClient.RequestException error) {
            return DockerRepositorySettingsResponse.failed(error.getCode());
        } catch (IllegalArgumentException | IllegalStateException error) {
            return DockerRepositorySettingsResponse.failed("updater_not_configured");
        }
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public ApiStandardResponse<DockerUpdaterProtocol.Settings> saveDockerSettings() {
        DockerUpdaterProtocol.Settings settings = getRequestBodyWithNullCheck(com.zrlog.admin.business.rest.request.DockerUpgradeSettingsRequest.class);
        DockerUpdaterProtocol.Settings saved = dockerClient().saveSettings(settings.repository, settings.channel);
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_SETTING, "Docker image repository");
        return new ApiStandardResponse<>(saved);
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
