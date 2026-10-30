package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.automation.*;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

public final class AutomationController extends BaseController {
    private final AutomationService tasks;
    private final AutomationDashboard dashboard;
    private final AutomationEngine engine;
    private final AutomationSettingsService settings;
    private final AdminRequestIdentity identities;
    private final AdminPageDataFactory pages;
    private final AutomationProposalService proposals;
    private final AutomationNotificationService notifications;
    private final AdminAuditService audit;

    @Inject
    public AutomationController(AutomationService tasks, AutomationDashboard dashboard, AutomationEngine engine,
                                AutomationSettingsService settings, AdminRequestIdentity identities, AdminPageDataFactory pages,
                                AutomationProposalService proposals, AutomationNotificationService notifications, AdminAuditService audit) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.engine = engine;
        this.settings = settings;
        this.identities = identities;
        this.pages = pages;
        this.proposals = proposals;
        this.notifications = notifications;
        this.audit = audit;
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.read")
    public ApiStandardResponse<java.util.List<com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse>> notices() throws Exception {
        response.addHeader("Cache-Control", "no-store");
        return new ApiStandardResponse<>(notifications.list(identities.current()));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.read")
    public ApiStandardResponse<Boolean> readNotice() throws Exception {
        var input = getRequestBodyWithNullCheck(com.zrlog.admin.business.rest.request.ReadMessageCenterNoticeRequest.class);
        return result(() -> notifications.markRead(identities.current(), input.getTaskKey()));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "automation.propose")
    public ApiStandardResponse<AutomationAssistant.Proposal> propose() throws Exception {
        response.addHeader("Cache-Control", "no-store");
        AutomationAssistant.Request input = getRequestBodyWithNullCheck(AutomationAssistant.Request.class);
        return result(() -> proposals.propose(identities.current(), input));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.read")
    public AdminPageDataResponse<AutomationViews.Overview> index() throws Exception {
        var actor = identities.current();
        return pages.response(actor, dashboard.overview(actor, request.getParaToInt("offset", 0)), "", "/admin/assistant");
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "automation.settings")
    public AdminPageDataResponse<AutomationViews.Settings> configuration() throws Exception {
        var actor = identities.current();
        return pages.response(actor, dashboard.settings(actor), "", "/admin/website/assistant");
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.save")
    public ApiStandardResponse<AutomationViews.Task> save() throws Exception {
        AutomationModels.Plan plan = getRequestBodyWithNullCheck(AutomationModels.Plan.class);
        return result(() -> {
            var saved = tasks.save(identities.current(), plan);
            record(AdminAuditAction.CONFIGURE_ASSISTANT_TASK, saved.name);
            return AutomationDashboard.task(saved);
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.enable")
    public ApiStandardResponse<AutomationViews.Task> enabled() throws Exception {
        AutomationViews.Change change = getRequestBodyWithNullCheck(AutomationViews.Change.class);
        if (change.enabled == null) throw new IllegalArgumentException("automation.invalidPlan");
        return result(() -> {
            var saved = tasks.setEnabled(identities.current(), change.id, change.revision, change.enabled);
            record(change.enabled ? AdminAuditAction.ENABLE_ASSISTANT_TASK : AdminAuditAction.PAUSE_ASSISTANT_TASK, saved.name);
            return AutomationDashboard.task(saved);
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.delete")
    public ApiStandardResponse<Boolean> delete() throws Exception {
        AutomationViews.Change change = getRequestBodyWithNullCheck(AutomationViews.Change.class);
        return result(() -> {
            var deleted = tasks.delete(identities.current(), change.id, change.revision);
            record(AdminAuditAction.DELETE_ASSISTANT_TASK, deleted.name);
            return true;
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.run")
    public ApiStandardResponse<AutomationViews.Run> run() throws Exception {
        AutomationViews.Change change = getRequestBodyWithNullCheck(AutomationViews.Change.class);
        return result(() -> {
            var run = engine.runNow(identities.current(), change.id, change.revision, settings.manualBudgetMillis());
            record(AdminAuditAction.RUN_ASSISTANT_TASK, run.taskName);
            return AutomationDashboard.run(run);
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.runs")
    public ApiStandardResponse<AutomationViews.Runs> runs() throws Exception {
        return new ApiStandardResponse<>(dashboard.runs(identities.current(), request.getParaToStr("taskId"),
                request.getParaToInt("page", 1), Integer.parseInt(request.getParaToStr("pageSize", "20"))));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "automation.runs")
    public ApiStandardResponse<AutomationViews.Run> detail() throws Exception {
        return new ApiStandardResponse<>(AutomationDashboard.run(tasks.run(identities.current(), request.getParaToStr("id"))));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "automation.settings")
    public ApiStandardResponse<AutomationViews.Settings> settings() throws Exception {
        return new ApiStandardResponse<>(dashboard.settings(identities.current()));
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "automation.settings")
    public ApiStandardResponse<AutomationSettingsService.SettingsView> saveSettings() throws Exception {
        AutomationViews.SettingsChange change = getRequestBodyWithNullCheck(AutomationViews.SettingsChange.class);
        if (change.internalEnabled == null || change.externalEnabled == null) throw new IllegalArgumentException("automation.invalidPlan");
        return result(() -> {
            var saved = settings.save(identities.current(), change.revision, change.internalEnabled, change.externalEnabled, change.externalBudgetSeconds);
            record(AdminAuditAction.UPDATE_ASSISTANT_SETTINGS, ""); return saved;
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "automation.token")
    public ApiStandardResponse<AutomationSettingsService.IssuedToken> rotateToken() throws Exception {
        AutomationViews.SettingsChange change = getRequestBodyWithNullCheck(AutomationViews.SettingsChange.class);
        response.addHeader("Cache-Control", "no-store");
        return result(() -> {
            var issued = settings.rotate(identities.current(), change.revision);
            record(AdminAuditAction.ROTATE_ASSISTANT_TOKEN, ""); return issued;
        });
    }

    @ResponseBody @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "automation.token")
    public ApiStandardResponse<AutomationSettingsService.SettingsView> revokeToken() throws Exception {
        AutomationViews.SettingsChange change = getRequestBodyWithNullCheck(AutomationViews.SettingsChange.class);
        return result(() -> {
            var saved = settings.revoke(identities.current(), change.revision);
            record(AdminAuditAction.REVOKE_ASSISTANT_TOKEN, ""); return saved;
        });
    }

    private void record(AdminAuditAction action, String content) {
        audit.record(AdminAuditContexts.from(request, identities.current().getUserId()), action, content);
    }

    private <T> ApiStandardResponse<T> result(Operation<T> operation) throws Exception {
        try { return new ApiStandardResponse<>(operation.run()); }
        catch (IllegalArgumentException | IllegalStateException error) {
            String key = error.getMessage();
            if (key == null || !key.startsWith("automation.")) throw error;
            ApiStandardResponse<T> result = new ApiStandardResponse<>();
            result.setError(400);
            result.setMessage(I18nUtil.getAdminBackendStringFromRes("admin." + key));
            return result;
        }
    }

    @FunctionalInterface
    private interface Operation<T> { T run() throws Exception; }
}
