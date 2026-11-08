package com.zrlog.admin.data.entity;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Entity;
import java.sql.Timestamp;

@Entity(table = "assistant_task_run", idColumn = "id")
public final class AssistantTaskRun extends BaseEntity<String> {
    private String taskId;
    private Integer ownerId;
    private String taskName;
    private String taskNameKey;
    private String taskType;

    /** Plan revision captured at execution time; independent of this row’s inherited version. */
    private Long taskRevision;

    private Timestamp scheduledAt;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private String trigger;
    private Integer triggeredBy;
    private Integer executionUserId;
    private String status;
    private String errorCode;
    private Boolean notifyOwner;
    private String summary;
    private String planSnapshot;
    private String result;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String value) {
        taskId = value;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer value) {
        ownerId = value;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String value) {
        taskName = value;
    }

    public String getTaskNameKey() {
        return taskNameKey;
    }

    public void setTaskNameKey(String value) {
        taskNameKey = value;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String value) {
        taskType = value;
    }

    public Long getTaskRevision() {
        return taskRevision;
    }

    public void setTaskRevision(Long value) {
        taskRevision = value;
    }

    public Timestamp getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Timestamp value) {
        scheduledAt = value;
    }

    public Timestamp getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Timestamp value) {
        startedAt = value;
    }

    public Timestamp getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Timestamp value) {
        finishedAt = value;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String value) {
        trigger = value;
    }

    public Integer getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(Integer value) {
        triggeredBy = value;
    }

    public Integer getExecutionUserId() {
        return executionUserId;
    }

    public void setExecutionUserId(Integer value) {
        executionUserId = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String value) {
        errorCode = value;
    }

    public Boolean getNotifyOwner() {
        return notifyOwner;
    }

    public void setNotifyOwner(Boolean value) {
        notifyOwner = value;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String value) {
        summary = value;
    }

    public String getPlanSnapshot() {
        return planSnapshot;
    }

    public void setPlanSnapshot(String value) {
        planSnapshot = value;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String value) {
        result = value;
    }
}
