package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.automation.*;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.CacheService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.util.List;
import java.util.function.Supplier;

/** Adapts host-owned maintenance services to the common task contract. */
public final class HostMaintenanceTaskProvider implements ScheduledTaskProvider {
    private final CacheService cache;
    private final ServiceComponent services;
    private final Supplier<UpdateVersionInfoPlugin> updates;

    public HostMaintenanceTaskProvider(CacheService cache, ServiceComponent services, Supplier<UpdateVersionInfoPlugin> updates) {
        this.cache = cache;
        this.services = services;
        this.updates = updates;
    }

    @Override
    public List<AutomationModels.Definition> definitions() {
        AutomationModels.Definition cache = definition("site.refresh-cache", "cache", 30);
        AutomationModels.Definition version = definition("system.check-version", "version", 50);
        version.available = updates.get() != null;
        version.unavailableReasonKey = "admin.assistant.error.taskUnavailable";
        return List.of(cache, version);
    }

    private static AutomationModels.Definition definition(String type, String key, int seconds) {
        AutomationModels.Definition definition = new AutomationModels.Definition();
        definition.type = type;
        definition.titleKey = "admin.assistant." + key + ".title";
        definition.descriptionKey = "admin.assistant." + key + ".description";
        definition.category = "maintenance";
        definition.systemTask = true;
        definition.enabledByDefault = true;
        definition.defaultSchedule = new AutomationModels.Schedule();
        definition.defaultSchedule.kind = "interval";
        definition.defaultSchedule.intervalMinutes = "site.refresh-cache".equals(type) ? 5 : 1440;
        definition.timeoutSeconds = seconds;
        definition.requiredActions = List.of(AccountAction.SYSTEM_MANAGE.getId());
        return definition;
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
        TaskAuthority.require(actor, AccountAction.SYSTEM_MANAGE.getId());
        if (!parameters.entrySet().isEmpty()) throw new IllegalArgumentException("automation.invalidParameters");
        return new JsonObject();
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        if ("site.refresh-cache".equals(type)) {
            cache.refreshInitData();
            return AutomationModels.Result.success("admin.assistant.cache.success.completed");
        }
        CheckVersionResponse check;
        try {
            check = services.upgrades().getCheckVersionResponse(true, updates.get());
        } catch (com.zrlog.business.exception.CheckUpgradeVersionException error) {
            AutomationModels.Result failed = AutomationModels.Result.success("admin.assistant.version.error.failed");
            failed.status = "failed";
            failed.actionPath = "/upgrade";
            return failed;
        }
        AutomationModels.Result result = AutomationModels.Result.success(Boolean.TRUE.equals(check.getUpgrade())
                ? "admin.assistant.version.status.available" : "admin.assistant.version.status.current");
        result.actionPath = "/upgrade";
        if (check.getVersion() != null) result.text = check.getVersion().getVersion();
        return result;
    }
}
