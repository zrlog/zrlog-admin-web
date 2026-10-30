package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;

import java.util.List;

/** Personal reminders work without an AI provider or a plugin-core process. */
public final class ReminderTaskProvider implements ScheduledTaskProvider {
    @Override
    public List<AutomationModels.Definition> definitions() {
        AutomationModels.Definition definition = new AutomationModels.Definition();
        definition.type = "assistant.reminder";
        definition.titleKey = "admin.automation.reminder.title";
        definition.descriptionKey = "admin.automation.reminder.description";
        definition.category = "personal";
        definition.timeoutSeconds = 1;
        definition.requiredActions = List.of(AccountAction.SESSION.getId());
        AutomationModels.Field text = new AutomationModels.Field();
        text.name = "text";
        text.type = "textarea";
        text.labelKey = "admin.automation.reminder.text";
        text.required = true;
        definition.fields = List.of(text);
        return List.of(definition);
    }

    @Override
    public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
        TaskAuthority.require(actor, AccountAction.SESSION.getId());
        if (!parameters.has("text") || !parameters.get("text").isJsonPrimitive()
                || !parameters.getAsJsonPrimitive("text").isString()) throw new IllegalArgumentException("automation.invalidParameters");
        String text = parameters.get("text").getAsString().trim();
        if (text.isEmpty() || text.length() > 4000) throw new IllegalArgumentException("automation.invalidParameters");
        JsonObject normalized = new JsonObject();
        normalized.addProperty("text", text);
        return normalized;
    }

    @Override
    public AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception {
        context.checkActive();
        AutomationModels.Result result = AutomationModels.Result.success("admin.automation.reminder.completed");
        result.text = parameters.get("text").getAsString();
        result.notifyOwner = true;
        return result;
    }
}
