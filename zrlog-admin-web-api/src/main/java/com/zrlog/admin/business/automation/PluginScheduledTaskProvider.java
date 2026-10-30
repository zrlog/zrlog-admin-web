package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.PluginSchedulerTickResult;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.util.List;
import java.util.function.Supplier;

/** One assistant plan triggers plugin-owned schedules; only this execution's children are copied. */
public final class PluginScheduledTaskProvider implements ScheduledTaskProvider {
    public static final String TYPE = "plugins.trigger-schedules";
    private static final String MANAGEMENT = "/plugin?page=runtime-scheduler";
    private final Supplier<PluginCorePlugin> plugin;

    public PluginScheduledTaskProvider(Supplier<PluginCorePlugin> plugin) { this.plugin = plugin; }

    @Override
    public List<AutomationModels.Definition> definitions() {
        AutomationModels.Definition definition = new AutomationModels.Definition();
        definition.type = TYPE;
        definition.titleKey = "admin.automation.plugins.title";
        definition.descriptionKey = "admin.automation.plugins.description";
        definition.category = "maintenance";
        definition.systemTask = true;
        definition.enabledByDefault = true;
        definition.requiredActions = List.of(AccountAction.PLUGIN_MANAGE.getId());
        definition.timeoutSeconds = 650;
        definition.available = plugin.get() != null;
        definition.unavailableReasonKey = "admin.automation.plugins.unavailable";
        definition.defaultSchedule = new AutomationModels.Schedule();
        definition.defaultSchedule.kind = "interval";
        definition.defaultSchedule.intervalMinutes = 1;
        return List.of(definition);
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
        TaskAuthority.require(actor, AccountAction.PLUGIN_MANAGE.getId());
        if (!TYPE.equals(type) || !parameters.entrySet().isEmpty()) throw new IllegalArgumentException("automation.invalidParameters");
        return new JsonObject();
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        prepare(type, parameters, context.actor());
        context.checkActive();
        PluginCorePlugin core = plugin.get();
        PluginSchedulerTickResult tick;
        try {
            // Leave time to store an unknown outcome if the host HTTP wait reaches its deadline.
            tick = core == null ? null : core.tickScheduledTasks(context.runId(), context.actor().getUserId(),
                    Math.max(0, context.remainingMillis() - 5000));
        } catch (java.io.IOException unconfirmed) {
            AutomationModels.Result result = AutomationModels.Result.attention("admin.automation.plugins.unconfirmed");
            result.status = "uncertain";
            result.externalRunId = context.runId();
            result.actionPath = MANAGEMENT;
            result.actionLabelKey = "admin.automation.plugins.manage";
            return result;
        }
        if (tick == null) {
            AutomationModels.Result result = AutomationModels.Result.attention("admin.automation.plugins.unavailable");
            result.actionPath = MANAGEMENT;
            result.actionLabelKey = "admin.automation.plugins.manage";
            return result;
        }
        AutomationModels.Result result = AutomationModels.Result.success("admin.automation.plugins.completed");
        result.actionPath = MANAGEMENT;
        result.actionLabelKey = "admin.automation.plugins.manage";
        result.externalRunId = tick.triggerId;
        boolean deferred = false;
        for (PluginSchedulerTickResult.Child source : tick.children) {
            AutomationModels.ChildResult child = new AutomationModels.ChildResult();
            child.id = source.automationId;
            child.name = source.name;
            child.status = "error".equals(source.status) ? "failed" : source.status;
            child.text = source.errorMessage;
            child.messageKey = "skipped".equals(source.status) ? skipKey(source.reason) : null;
            child.actionPath = MANAGEMENT;
            child.actionLabelKey = "admin.automation.plugins.manage";
            child.externalRunId = source.runId;
            child.startedAt = source.startedAt;
            child.finishedAt = source.finishedAt;
            result.children.add(child);
            deferred |= "insufficient_budget".equals(source.reason) || "plugin_not_ready".equals(source.reason);
        }
        if (tick.uncertainCount > 0) {
            result.status = "needs_attention";
            result.messageKey = "admin.automation.plugins.uncertain";
        } else if (tick.failedCount > 0 || deferred) {
            result.status = "failed";
            result.messageKey = "admin.automation.plugins.incomplete";
        } else if (result.children.isEmpty()) {
            result.messageKey = "admin.automation.plugins.empty";
        }
        return result;
    }

    private static String skipKey(String reason) {
        if ("insufficient_budget".equals(reason)) return "admin.automation.plugins.budget";
        if ("plugin_not_ready".equals(reason)) return "admin.automation.plugins.notReady";
        if ("already_running".equals(reason)) return "admin.automation.plugins.running";
        return "admin.automation.plugins.notDue";
    }
}
