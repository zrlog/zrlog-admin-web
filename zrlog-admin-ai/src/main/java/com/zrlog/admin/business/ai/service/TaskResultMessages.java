package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.automation.AutomationMessages;
import com.zrlog.admin.business.automation.AutomationModels;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.util.I18nUtil;
import java.util.Objects;

/** Standard feedback is localized at the read boundary; model replies never replace its facts. */
final class TaskResultMessages {
    private TaskResultMessages() {}

    static AIContentEntry entry(AutomationModels.Run run) {
        var entry = new AIContentEntry("assistant", null);
        entry.setMessageType("taskResult");
        entry.setMessageId("task-result:" + run.id);
        entry.setCreatedAt(run.updatedAt == null ? run.finishedAt : run.updatedAt);
        var result = new AIContentEntry.TaskResult();
        result.runId = run.id;
        result.version = run.version;
        result.status = run.status;
        if (run.errorCode != null) result.messageKey = AutomationMessages.errorKey(run.errorCode);
        else if (run.result != null) {
            result.messageKey = run.result.messageKey;
            result.messageArguments = run.result.messageArguments;
        }
        if (run.result != null) {
            result.text = run.result.text;
            result.recovered = run.result.recovered;
        }
        entry.setTaskResult(result);
        return entry;
    }

    static void localize(AIContentEntry entry) {
        var result = entry.getTaskResult();
        if (result == null) return;
        String summary = result.messageKey == null ? "" : text(result.messageKey);
        for (int i = 0; i < result.messageArguments.size(); i++)
            summary = summary.replace("{" + i + "}", result.messageArguments.get(i));
        String status = Objects.toString(result.status, "uncertain");
        if (!java.util.Set.of("success", "submitted", "failed", "needs_attention", "uncertain")
                .contains(status)) status = "uncertain";
        // entry() selects the execution error before any saved progress summary.
        String statusKey = "needs_attention".equals(status) ? "needsAttention" : status;
        String message =
                summary.isBlank() ? text("admin.assistant.feedback." + statusKey) : summary;
        if (result.recovered)
            message = text("admin.assistant.feedback.recovered") + "\n\n" + message;
        if (result.text != null && !result.text.isBlank() && !message.equals(result.text))
            message += "\n\n" + result.text;
        entry.setContent(message);
    }

    private static String text(String key) {
        return I18nUtil.getAdminBackendStringFromRes(key);
    }
}
