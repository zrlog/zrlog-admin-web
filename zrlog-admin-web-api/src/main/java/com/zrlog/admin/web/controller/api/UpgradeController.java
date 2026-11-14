package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.automation.AutomaticUpgradeTaskProvider;
import com.zrlog.admin.business.automation.UpgradeExecutionCoordinator;
import com.zrlog.admin.business.rest.request.ExecuteUpgradeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.UpgradeCheckResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.service.UpgradeReadinessMessages;
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
import com.zrlog.common.vo.Version;
import com.zrlog.data.security.AccountAction;
import com.zrlog.updater.spi.UpdateCommand;
import com.zrlog.updater.spi.UpdateTask;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class UpgradeController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final AdminAuditService adminAuditService;
    private final MessageCenterOperationService messageCenterOperationService;

    private final AdminPageDataFactory pages;

    private final UpgradeService upgrades;
    private final Supplier<UpdateVersionInfoPlugin> updatePlugin;
    private final UpgradeExecutionCoordinator executions;

    @Inject
    public UpgradeController(
            UpgradeService upgrades,
            Supplier<UpdateVersionInfoPlugin> updatePlugin,
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            MessageCenterOperationService messageCenterOperationService,
            AdminRequestIdentity requestIdentity,
            UpgradeExecutionCoordinator executions) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;
        this.messageCenterOperationService = messageCenterOperationService;

        this.pages = pages;
        this.upgrades = upgrades;
        this.updatePlugin = updatePlugin;
        this.executions = executions;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeCheck")
    public AdminPageDataResponse<PreCheckVersionResponse> index() throws SQLException {
        // Keep the public check fresh; page rendering and task recovery can use cached metadata.
        boolean apiCheck =
                request.getUri().endsWith("/api" + Constants.ADMIN_URI_BASE_PATH + "/upgrade");
        boolean fetch = "true".equals(request.getParaToStr("fetch", apiCheck ? "true" : "false"));
        UpgradeCheckResponse preCheckVersionResponse =
                BeanUtil.convert(
                        upgrades.preUpgradeVersion(fetch, updatePlugin.get()),
                        UpgradeCheckResponse.class);
        preCheckVersionResponse.setExecution(
                executions.refresh(
                        requestIdentity.current(),
                        requestId -> upgrades.upgradeStatus(requestId, I18nUtil.getBackend())));
        UpgradeReadinessMessages.apply(preCheckVersionResponse);
        return pages.response(
                requestIdentity.optional(),
                preCheckVersionResponse,
                Objects.equals(preCheckVersionResponse.getUpgrade(), true)
                        ? ""
                        : I18nUtil.getAdminBackendStringFromRes("admin.upgrade.version.notFound"),
                request.getUri());
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgradeNotice")
    public ApiStandardResponse<CheckVersionResponse> notice() {
        boolean fetch = Objects.equals("true", request.getParaToStr("fetch", "false"));
        return new ApiStandardResponse<>(
                upgrades.getCheckVersionResponse(fetch, updatePlugin.get()), "");
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public void doUpgrade() throws Exception {
        ExecuteUpgradeRequest upgradeRequest =
                getRequestBodyWithNullCheck(ExecuteUpgradeRequest.class);
        try {
            executeUpgrade(upgradeRequest);
        } catch (UpgradeExecutionCoordinator.Conflict conflict) {
            // Keep a business rejection distinguishable from a stream interrupted by process
            // replacement.
            var result = new ApiStandardResponse<>(conflict.execution);
            result.setError(409);
            result.setMessage(
                    I18nUtil.getAdminBackendStringFromRes(
                            "admin.assistant.upgrade.status.pending"));
            response.renderJson(result);
        }
    }

    private void executeUpgrade(ExecuteUpgradeRequest input) throws Exception {
        Map<String, Object> backend = I18nUtil.getBackend();
        UpdateCommand command = input.getTask();
        boolean legacyResponse = command == null;
        if (command == null) {
            PreCheckVersionResponse check = upgrades.preUpgradeVersion(true, updatePlugin.get());
            Version target = Boolean.TRUE.equals(check.getUpgrade()) ? check.getVersion() : null;
            if (target == null || !Boolean.TRUE.equals(check.getOnlineUpgradable())) {
                if (isSseRequest()) {
                    AdminSseEmitter.write(
                            response,
                            "admin-upgrade",
                            "sse-error",
                            emitter ->
                                    emitter.send(
                                            "response",
                                            new ApiStandardResponse<>(
                                                    manualResponse(
                                                            target, emitter::send, backend))));
                } else {
                    response.renderJson(
                            new ApiStandardResponse<>(
                                    manualResponse(target, UpgradeProgressListener.NONE, backend)));
                }
                return;
            }
            command = new UpdateCommand();
            command.requestId = java.util.UUID.randomUUID().toString();
            command.version =
                    target.getArtifactVersion() == null
                            ? target.getVersion()
                            : target.getArtifactVersion();
            command.buildId = target.getBuildId();
            command.channel =
                    target.getChannel() == null
                            ? (command.version.contains("SNAPSHOT") ? "preview" : "release")
                            : target.getChannel();
        }
        command.validate();
        Version target = new Version();
        target.setVersion(command.version);
        target.setBuildId(command.buildId);
        var receipt =
                executions.claimManual(
                        requestIdentity.current(),
                        target,
                        command.requestId,
                        command.channel,
                        input.getReviewedExecutionId());
        UpdateCommand selected = command;
        if (isSseRequest()) {
            AdminSseEmitter.write(
                    response,
                    "admin-upgrade",
                    "sse-error",
                    emitter -> {
                        submit(selected, target, receipt, emitter::send, backend);
                        emitter.send(
                                "response",
                                taskResponse(
                                        upgrades.watchUpgrade(
                                                selected.requestId, emitter::send, backend),
                                        legacyResponse));
                    });
        } else {
            response.renderJson(
                    taskResponse(
                            submit(
                                    selected,
                                    target,
                                    receipt,
                                    UpgradeProgressListener.NONE,
                                    backend),
                            legacyResponse));
        }
    }

    private UpgradeProcessResponse manualResponse(
            Version target, UpgradeProgressListener listener, Map<String, Object> backend) {
        UpgradeProcessResponse result = upgrades.doUpgradeVersion(target, listener, backend);
        recordUpgradeAudit(target);
        if (!Boolean.TRUE.equals(result.getFinish()))
            messageCenterOperationService.recordUpgradeError();
        return result;
    }

    private UpdateTask submit(
            UpdateCommand command,
            Version target,
            AutomaticUpgradeTaskProvider.Receipt receipt,
            UpgradeProgressListener listener,
            Map<String, Object> backend)
            throws Exception {
        boolean submitted = false;
        try {
            UpdateTask task = upgrades.submitUpgrade(command, listener, backend);
            submitted = task != null && !task.failed();
            recordUpgradeAudit(target);
            if (task != null && task.failed()) messageCenterOperationService.recordUpgradeError();
            return task;
        } finally {
            executions.finish(receipt, submitted);
        }
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.upgrade")
    public void status() throws Exception {
        String requestId = request.getParaToStr("requestId");
        UpdateCommand.validateId(requestId);
        Map<String, Object> backend = I18nUtil.getBackend();
        if (isSseRequest()) {
            AdminSseEmitter.write(
                    response,
                    "admin-upgrade",
                    "sse-error",
                    emitter ->
                            emitter.send(
                                    "response",
                                    taskResponse(
                                            upgrades.watchUpgrade(
                                                    requestId, emitter::send, backend))));
        } else {
            response.renderJson(taskResponse(upgrades.upgradeStatus(requestId, backend)));
        }
    }

    private ApiStandardResponse<UpdateTask> taskResponse(UpdateTask task) throws SQLException {
        executions.observe(task);
        ApiStandardResponse<UpdateTask> result =
                new ApiStandardResponse<>(task, task == null ? "" : task.message);
        if (task != null && task.failed()) result.setError(1);
        return result;
    }

    private ApiStandardResponse<?> taskResponse(UpdateTask task, boolean legacy)
            throws SQLException {
        if (!legacy) return taskResponse(task);
        executions.observe(task);
        var result =
                new ApiStandardResponse<>(
                        new UpgradeProcessResponse(
                                task != null && !task.failed(), task == null ? "" : task.message));
        if (task != null && task.failed()) result.setError(1);
        return result;
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    private void recordUpgradeAudit(Version target) {
        this.adminAuditService.record(
                AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()),
                AdminAuditAction.EXECUTE_UPGRADE,
                target == null ? "" : target.getVersion());
    }
}
