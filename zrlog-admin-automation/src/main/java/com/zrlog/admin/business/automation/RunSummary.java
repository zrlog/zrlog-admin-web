package com.zrlog.admin.business.automation;

import java.sql.Timestamp;

/** Read-only list/notice projection; excludes planSnapshot, result and child payloads. */
public final class RunSummary {
    static final String COLUMNS =
            "r.`id`,r.`version`,r.`updatedAt`,r.`taskId`,r.`ownerId`,r.`taskName`,r.`taskNameKey`,r.`taskType`,r.`taskRevision`,r.`scheduledAt`,r.`startedAt`,r.`finishedAt`,r.`trigger`,r.`triggeredBy`,r.`executionUserId`,r.`status`,r.`errorCode`,r.`summary`";
    public String id;
    public long version;
    public String taskId;
    public int ownerId;
    public String taskName;
    public String taskNameKey;
    public String taskType;
    public long taskRevision;
    public Timestamp scheduledAt;
    public Timestamp startedAt;
    public Timestamp updatedAt;
    public Timestamp finishedAt;
    public String trigger;
    public Integer triggeredBy;
    public Integer executionUserId;
    public String status;
    public String errorCode;
    public String summary;

    AutomationModels.Run value() {
        var value = new AutomationModels.Run();
        value.id = id;
        value.version = version;
        value.taskId = taskId;
        value.ownerId = ownerId;
        value.taskName = taskName;
        value.taskNameKey = taskNameKey;
        value.taskType = taskType;
        value.taskRevision = taskRevision;
        value.scheduledAt = AssistantRecordMapper.millis(scheduledAt);
        value.startedAt = AssistantRecordMapper.millis(startedAt);
        value.updatedAt = AssistantRecordMapper.millis(updatedAt);
        value.finishedAt = AssistantRecordMapper.millis(finishedAt);
        value.trigger = trigger;
        value.triggeredBy = triggeredBy;
        value.executionUserId = executionUserId;
        value.status = status;
        value.errorCode = errorCode;
        value.result = AutomationStore.JSON.fromJson(summary, AutomationModels.Result.class);
        return value;
    }
}
