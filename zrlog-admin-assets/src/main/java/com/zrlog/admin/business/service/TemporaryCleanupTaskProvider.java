package com.zrlog.admin.business.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.automation.*;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

public final class TemporaryCleanupTaskProvider implements ScheduledTaskProvider {
    private final DbFileService files;
    private final AccountPermissionService permissions;
    private final Provider<WebSite> websites;

    @Inject
    public TemporaryCleanupTaskProvider(DbFileService files, AccountPermissionService permissions, Provider<WebSite> websites) {
        this.files = files;
        this.permissions = permissions;
        this.websites = websites;
    }

    @Override
    public List<AutomationModels.Definition> definitions() {
        var definition = new AutomationModels.Definition();
        definition.type = "system.cleanup-temporary";
        definition.titleKey = "admin.automation.cleanup.title";
        definition.descriptionKey = "admin.automation.cleanup.description";
        definition.category = "maintenance";
        definition.timeoutSeconds = 30;
        definition.requiredActions = List.of(AccountAction.SYSTEM_MANAGE.getId());
        var days = new AutomationModels.Field();
        days.name = "retentionDays";
        days.type = "number";
        days.labelKey = "admin.automation.cleanup.days";
        days.required = true;
        days.minimum = 1L;
        days.maximum = 3650L;
        definition.fields = List.of(days);
        return List.of(definition);
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
        permissions.require(actor, AccountAction.SYSTEM_MANAGE);
        int days;
        try { days = parameters.get("retentionDays").getAsBigDecimal().intValueExact(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("automation.invalidParameters", error); }
        if (days < 1 || days > 3650) throw new IllegalArgumentException("automation.invalidParameters");
        var normalized = new JsonObject();
        normalized.addProperty("retentionDays", days);
        return normalized;
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        permissions.require(context.actor(), AccountAction.SYSTEM_MANAGE);
        String cursorKey = "assistant.cleanup.cursor:" + parameters.get("retentionDays").getAsInt();
        Object stored = websites.get().queryFirstObj("SELECT value FROM website WHERE name=?", cursorKey);
        var batch = files.cleanupTemporary(parameters.get("retentionDays").getAsInt(), stored == null ? "" : stored.toString(), context);
        context.checkActive();
        websites.get().updateByKV(cursorKey, batch.cursor);
        var result = AutomationModels.Result.success("admin.automation.cleanup.completed");
        result.messageArguments = List.of(Integer.toString(batch.removed), Integer.toString(batch.scanned));
        return result;
    }
}
