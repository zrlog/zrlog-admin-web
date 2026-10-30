package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.data.security.AccountAccess;

import java.util.List;

/** Optional capability of an application plugin; registration also works without a resident timer. */
public interface ScheduledTaskProvider {
    List<AutomationModels.Definition> definitions();

    /** Validate and normalize provider parameters with the user's current permissions. */
    JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) throws Exception;

    /** The provider owns its cohesive workflow, child steps and final outcome; the scheduler executes only this task. */
    AutomationModels.Result execute(String type, JsonObject parameters, TaskExecutionContext context) throws Exception;

    /** Read-only proof after interruption. Never repeat the action; null means the outcome is unknown. */
    default AutomationModels.Result recover(String type, JsonObject parameters, String runId, AccountAccess actor) throws Exception {
        return null;
    }
}
