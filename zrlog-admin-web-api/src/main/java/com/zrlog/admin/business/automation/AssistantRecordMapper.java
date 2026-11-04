package com.zrlog.admin.business.automation;

import static com.zrlog.admin.business.automation.AutomationStore.JSON;

import com.zrlog.admin.data.entity.*;
import java.sql.Timestamp;
import java.util.*;

/** Explicit conversion keeps SQL dates and queryable fields outside provider-owned JSON. */
final class AssistantRecordMapper {
    private AssistantRecordMapper() {}

    static Timestamp timestamp(Long value) {
        return value == null ? null : new Timestamp(value);
    }

    static Long millis(Timestamp value) {
        return value == null ? null : value.getTime();
    }

    static String dateParameter(long value) {
        return new Timestamp(value).toString();
    }

    static AssistantTask taskEntity(StoredTask value) {
        AssistantTask row = new AssistantTask();
        row.setId(value.id);
        row.setOwnerId(value.ownerId);
        row.setName(value.name);
        row.setNameKey(value.nameKey);
        row.setType(value.type);
        row.setEnabled(value.enabled);
        row.setRevision(value.revision);
        row.setUpdatedBy(value.updatedBy);
        row.setLastStatus(value.lastStatus);
        row.setSchedule(JSON.toJson(value.schedule));
        row.setParameters(JSON.toJson(value.parameters));
        row.setExecutionActions(JSON.toJson(value.executionActions));
        row.setActiveRun(JSON.toJson(value.activeRun));
        row.setNextRunAt(timestamp(value.nextRunAt));
        row.setLastRunAt(timestamp(value.lastRunAt));
        row.setActiveRunId(value.activeRun == null ? null : value.activeRun.id);
        row.setLeaseUntil(value.activeRun == null ? null : timestamp(value.leaseUntil));
        row.setDueAt(
                value.activeRun != null
                        ? timestamp(value.leaseUntil)
                        : value.enabled ? timestamp(value.nextRunAt) : null);
        return row;
    }

    static StoredTask taskValue(AssistantTask row) {
        StoredTask value = new StoredTask();
        value.id = row.getId();
        value.ownerId = row.getOwnerId();
        value.name = row.getName();
        value.nameKey = row.getNameKey();
        value.type = row.getType();
        value.enabled = row.getEnabled();
        value.revision = row.getRevision();
        value.updatedBy = row.getUpdatedBy();
        value.lastStatus = row.getLastStatus();
        value.schedule = JSON.fromJson(row.getSchedule(), AutomationModels.Schedule.class);
        value.parameters = JSON.fromJson(row.getParameters(), com.google.gson.JsonObject.class);
        value.activeRun = JSON.fromJson(row.getActiveRun(), AutomationModels.Run.class);
        value.executionActions =
                new ArrayList<>(
                        Arrays.asList(JSON.fromJson(row.getExecutionActions(), String[].class)));
        value.createdAt = row.getCreatedAt().getTime();
        value.updatedAt = row.getUpdatedAt().getTime();
        value.nextRunAt = millis(row.getNextRunAt());
        value.lastRunAt = millis(row.getLastRunAt());
        value.leaseUntil = row.getLeaseUntil() == null ? 0 : row.getLeaseUntil().getTime();
        return value;
    }

    static AssistantTaskRun runEntity(AutomationModels.Run value) {
        AssistantTaskRun row = new AssistantTaskRun();
        row.setId(value.id);
        row.setTaskId(value.taskId);
        row.setOwnerId(value.ownerId);
        row.setTaskName(value.taskName);
        row.setTaskNameKey(value.taskNameKey);
        row.setTaskType(value.taskType);
        row.setTaskRevision(value.taskRevision);
        row.setTrigger(value.trigger);
        row.setTriggeredBy(value.triggeredBy);
        row.setExecutionUserId(value.executionUserId);
        row.setStatus(value.status);
        row.setErrorCode(value.errorCode);
        row.setScheduledAt(timestamp(value.scheduledAt));
        row.setStartedAt(timestamp(value.startedAt));
        row.setFinishedAt(timestamp(value.finishedAt));
        row.setNotifyOwner(
                value.finishedAt != null
                        && !"running".equals(value.status)
                        && (Set.of("failed", "uncertain", "needs_attention").contains(value.status)
                                || value.result != null && value.result.notifyOwner));
        row.setPlanSnapshot(JSON.toJson(value.planSnapshot));
        row.setResult(JSON.toJson(value.result));
        if (value.result != null) {
            AutomationModels.Result summary = new AutomationModels.Result();
            summary.status = value.result.status;
            summary.notifyOwner = value.result.notifyOwner;
            summary.messageKey = value.result.messageKey;
            summary.messageArguments = value.result.messageArguments;
            summary.text =
                    value.result.text == null
                            ? null
                            : value.result.text.substring(
                                    0, Math.min(1000, value.result.text.length()));
            summary.actionPath = value.result.actionPath;
            summary.actionLabelKey = value.result.actionLabelKey;
            row.setSummary(JSON.toJson(summary));
        }
        return row;
    }

    static AutomationModels.Run runValue(AssistantTaskRun row) {
        AutomationModels.Run value = new AutomationModels.Run();
        value.id = row.getId();
        value.version = row.getVersion();
        value.taskId = row.getTaskId();
        value.ownerId = row.getOwnerId();
        value.taskName = row.getTaskName();
        value.taskNameKey = row.getTaskNameKey();
        value.taskType = row.getTaskType();
        value.taskRevision = row.getTaskRevision();
        value.trigger = row.getTrigger();
        value.triggeredBy = row.getTriggeredBy();
        value.executionUserId = row.getExecutionUserId();
        value.status = row.getStatus();
        value.errorCode = row.getErrorCode();
        value.scheduledAt = millis(row.getScheduledAt());
        value.startedAt = millis(row.getStartedAt());
        value.finishedAt = millis(row.getFinishedAt());
        value.planSnapshot = JSON.fromJson(row.getPlanSnapshot(), AutomationModels.Plan.class);
        value.result = JSON.fromJson(row.getResult(), AutomationModels.Result.class);
        return value;
    }
}
