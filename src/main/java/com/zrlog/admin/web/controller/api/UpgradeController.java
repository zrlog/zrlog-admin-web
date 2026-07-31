package com.zrlog.admin.web.controller.api;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.ExecuteUpgradeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
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
    public AdminPageDataResponse<PreCheckVersionResponse> index() {
        PreCheckVersionResponse preCheckVersionResponse = AdminStaticService.getInstance().getUpgradeService().preUpgradeVersion(true, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class));
        return new AdminPageDataResponse<>(preCheckVersionResponse,
                Objects.equals(preCheckVersionResponse.getUpgrade(), true) ? "" : I18nUtil.getAdminBackendStringFromRes("admin.upgrade.version.notFound"), request.getUri());
    }

    @ResponseBody
    public ApiStandardResponse<CheckVersionResponse> notice() {
        boolean fetch = Objects.equals("true", request.getParaToStr("fetch", "false"));
        return new ApiStandardResponse<>(
                AdminStaticService.getInstance().getUpgradeService().getCheckVersionResponse(fetch, Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class)),
                ""
        );
    }


    @ResponseBody
    public void doUpgrade() throws IOException {
        ExecuteUpgradeRequest upgradeRequest = getOptionalUpgradeRequest();
        UpdateVersionInfoPlugin plugin = Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class);
        Map<String, Object> backend = I18nUtil.getBackend();
        if (!isSseRequest()) {
            UpgradeProcessResponse upgradeProcessResponse;
            try {
                upgradeProcessResponse = AdminStaticService.getInstance().getUpgradeService()
                        .doUpgrade(plugin, UpgradeProgressListener.NONE, backend,
                                upgradeRequest.isBackupRiskAccepted());
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
                        .doUpgrade(plugin, emitter::send, backend, upgradeRequest.isBackupRiskAccepted());
                new MessageCenterOperationService().recordUpgradeResult(upgradeProcessResponse);
            } catch (Exception e) {
                new MessageCenterOperationService().recordUpgradeError(e.getMessage());
                throw e;
            }
            recordUpgradeAudit(upgradeRequest);
            emitter.send("response", new ApiStandardResponse<>(upgradeProcessResponse));
        });
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    private ExecuteUpgradeRequest getOptionalUpgradeRequest() {
        if (Objects.isNull(request.getInputStream())) {
            return new ExecuteUpgradeRequest();
        }
        String body = IOUtil.getStringInputStream(request.getInputStream());
        if (StringUtils.isEmpty(body)) {
            return new ExecuteUpgradeRequest();
        }
        ExecuteUpgradeRequest upgradeRequest = new Gson().fromJson(body, ExecuteUpgradeRequest.class);
        return Objects.requireNonNull(upgradeRequest, "Upgrade request must be a JSON object");
    }

    private void recordUpgradeAudit(ExecuteUpgradeRequest requestBody) {
        new AdminAuditService().record(request, AdminAuditAction.EXECUTE_UPGRADE,
                requestBody.isBackupRiskAccepted() ? "backupRiskAccepted" : "");
    }

}
