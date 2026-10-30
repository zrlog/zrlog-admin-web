package com.zrlog.admin.business.automation;

import com.google.gson.JsonObject;
import com.zrlog.common.Validator;

import java.util.ArrayList;
import java.util.List;

/** Public task contracts. Parameters are the dynamic, provider-owned JSON boundary. */
public final class AutomationModels {
    private AutomationModels() { }

    public static class Definition {
        public String type;
        public String titleKey;
        public String descriptionKey;
        public String title;
        public String description;
        public String category;
        /** A preconfigured maintenance task, managed through enablement and settings. */
        public boolean systemTask;
        public boolean enabledByDefault;
        public boolean requiresAi;
        public boolean available = true;
        public String unavailableReasonKey;
        public String unavailableReason;
        public int timeoutSeconds = 30;
        public List<String> requiredActions = new ArrayList<>();
        public List<String> optionalActions = new ArrayList<>();
        public List<Field> fields = new ArrayList<>();
        public Schedule defaultSchedule;
        public JsonObject defaultParameters = new JsonObject();
    }

    public static class Field {
        public String name;
        public String type;
        public String labelKey;
        public String label;
        public boolean required;
        public boolean readOnly;
        public boolean requiresConfirmation;
        public Long minimum;
        public Long maximum;
    }

    public static class Schedule {
        public String kind = "once";
        public String timezone = "UTC";
        public Long at;
        public Integer intervalMinutes;
        public String time;
        public List<Integer> weekdays = new ArrayList<>();
    }

    public static class Plan implements Validator {
        public String id;
        public Long revision;
        public String name;
        public String type;
        public boolean enabled = true;
        public Schedule schedule;
        public JsonObject parameters = new JsonObject();

        @Override
        public void doValid() {
            if (name == null || name.isBlank() || name.length() > 160 || type == null || type.isBlank()
                    || schedule == null || parameters == null || parameters.toString().length() > 32768
                    || (id != null && (revision == null || revision < 1))) {
                throw new IllegalArgumentException("automation.invalidPlan");
            }
        }
    }

    /** A provider-owned step or group within one execution, without an independent schedule or lifecycle. */
    public static class ChildResult {
        public String id;
        public String name;
        public String status;
        public String messageKey;
        public String summary;
        public String text;
        public String actionPath;
        public String actionLabelKey;
        public String actionLabel;
        public String externalRunId;
        public Long startedAt;
        public Long finishedAt;
        public List<ChildResult> children = new ArrayList<>();
    }

    public static class Result {
        public String status = "success";
        /** Show a successful outcome in the owner's inbox. Failures always appear there. */
        public boolean notifyOwner;
        public String messageKey;
        public List<String> messageArguments = new ArrayList<>();
        public String text;
        public String actionPath;
        public String actionLabelKey;
        public String actionLabel;
        public String externalRunId;

        public List<ChildResult> children = new ArrayList<>();

        public static Result success(String key) {
            Result result = new Result();
            result.messageKey = key;
            return result;
        }

        public static Result attention(String key) {
            Result result = success(key);
            result.status = "needs_attention";
            return result;
        }
    }

    public static class Run {
        public String id;
        public String taskId;
        public int ownerId;
        public String taskName;
        public String taskType;
        public long taskRevision;
        public Plan planSnapshot;
        public long scheduledAt;
        public long startedAt;
        public Long finishedAt;
        public String trigger;
        public Integer triggeredBy;
        public Integer executionUserId;
        public String status;
        public Result result;
        public String errorCode;
        public Long noticeReadAt;
    }

    public static class TickResult {
        public int executed;
        public int failed;
        public int skipped;
        public int needsAttention;
        public boolean budgetExhausted;
        public List<String> insufficientBudgetTaskIds = new ArrayList<>();
        public int requiredBudgetSeconds;
        public List<String> runIds = new ArrayList<>();
    }
}
