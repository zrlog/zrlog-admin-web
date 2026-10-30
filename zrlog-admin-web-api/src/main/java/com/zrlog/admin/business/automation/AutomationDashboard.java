package com.zrlog.admin.business.automation;

import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/** Produces account-scoped views, without stored identity grants or scheduler credential digests. */
public final class AutomationDashboard {
    private final AutomationService tasks;
    private final AutomationStore store;
    private final AutomationSettingsService settings;
    private final AutomationTickService ticks;
    private final TaskAuthority authority;
    private final AutomationProposalService proposals;

    @Inject
    public AutomationDashboard(AutomationService tasks, AutomationStore store, AutomationSettingsService settings,
                                AutomationTickService ticks, TaskAuthority authority, AutomationProposalService proposals) {
        this.tasks = tasks;
        this.store = store;
        this.settings = settings;
        this.ticks = ticks;
        this.authority = authority;
        this.proposals = proposals;
    }

    public AutomationViews.Overview overview(AccountAccess actor, int offset) throws SQLException {
        actor = authority.current(actor);
        AutomationViews.Overview response = new AutomationViews.Overview();
        for (AutomationModels.Definition definition : tasks.definitions(actor)) {
            AutomationModels.Definition copy = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(definition), AutomationModels.Definition.class);
            copy.title = text(copy.titleKey);
            copy.description = text(copy.descriptionKey);
            copy.unavailableReason = copy.available ? null : text(copy.unavailableReasonKey);
            for (AutomationModels.Field field : copy.fields) field.label = text(field.labelKey);
            response.definitions.add(copy);
        }
        response.aiAvailable = proposals.available(actor);
        for (StoredTask task : tasks.tasks(actor, offset)) response.tasks.add(task(task));
        response.total = store.taskCount(actor.getUserId());
        response.canManageScheduler = AccountAction.SYSTEM_MANAGE.allowed(actor);
        return response;
    }

    public AutomationViews.Runs runs(AccountAccess actor, String taskId, int page, int pageSize) throws SQLException {
        actor = authority.current(actor);
        AutomationViews.Runs response = new AutomationViews.Runs();
        response.page = Math.max(1, Math.min(100000, page));
        response.pageSize = Math.max(1, Math.min(100, pageSize));
        for (AutomationModels.Run run : tasks.runs(actor, taskId, response.pageSize, (response.page - 1) * response.pageSize)) {
            response.items.add(run(run));
        }
        response.total = store.runCount(actor.getUserId(), taskId);
        return response;
    }

    public AutomationViews.Settings settings(AccountAccess actor) throws SQLException {
        AutomationViews.Settings response = new AutomationViews.Settings();
        response.settings = settings.read(actor);
        response.internal = ticks.health("internal");
        response.external = ticks.health("external");
        return response;
    }

    public static AutomationViews.Task task(StoredTask task) {
        AutomationViews.Task view = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(task), AutomationViews.Task.class);
        view.activeRunId = task.activeRun == null ? null : task.activeRun.id;
        return view;
    }

    public static AutomationViews.Run run(AutomationModels.Run run) {
        AutomationViews.Run view = AutomationStore.JSON.fromJson(AutomationStore.JSON.toJson(run), AutomationViews.Run.class);
        if (run.errorCode != null) view.summary = text("admin." + run.errorCode);
        else if (run.result != null && run.result.messageKey != null) {
            view.summary = text(run.result.messageKey);
            for (int i = 0; i < run.result.messageArguments.size(); i++) {
                view.summary = view.summary.replace("{" + i + "}", run.result.messageArguments.get(i));
            }
        }
        if (view.result != null) {
            view.result.actionLabel = text(view.result.actionLabelKey);
            localizeChildren(view.result.children);
        }
        return view;
    }

    private static void localizeChildren(List<AutomationModels.ChildResult> children) {
        if (children == null) return;
        for (AutomationModels.ChildResult child : children) {
            child.summary = child.messageKey == null ? null : text(child.messageKey);
            child.actionLabel = text(child.actionLabelKey);
            localizeChildren(child.children);
        }
    }

    private static String text(String key) {
        return key == null ? "" : I18nUtil.getAdminBackendStringFromRes(key);
    }
}
