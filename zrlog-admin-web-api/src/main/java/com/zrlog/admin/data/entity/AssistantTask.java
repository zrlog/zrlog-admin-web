package com.zrlog.admin.data.entity;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Entity;
import java.sql.Timestamp;

@Entity(table = "assistant_task", idColumn = "id")
public final class AssistantTask extends BaseEntity<String> {
    private Integer ownerId;
    private String name;
    private String nameKey;
    private String type;
    private Boolean enabled;

    /** Plan edits only; scheduler state changes advance the inherited row version instead. */
    private Long revision;

    private Integer updatedBy;
    private String schedule;
    private String parameters;
    private String executionActions;
    private Timestamp nextRunAt;
    private Timestamp lastRunAt;
    private String lastStatus;
    private String activeRunId;
    private String activeRun;
    private Timestamp leaseUntil;
    private Timestamp dueAt;

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer value) {
        ownerId = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void setNameKey(String value) {
        nameKey = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean value) {
        enabled = value;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long value) {
        revision = value;
    }

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Integer value) {
        updatedBy = value;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String value) {
        schedule = value;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String value) {
        parameters = value;
    }

    public String getExecutionActions() {
        return executionActions;
    }

    public void setExecutionActions(String value) {
        executionActions = value;
    }

    public Timestamp getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Timestamp value) {
        nextRunAt = value;
    }

    public Timestamp getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Timestamp value) {
        lastRunAt = value;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String value) {
        lastStatus = value;
    }

    public String getActiveRunId() {
        return activeRunId;
    }

    public void setActiveRunId(String value) {
        activeRunId = value;
    }

    public String getActiveRun() {
        return activeRun;
    }

    public void setActiveRun(String value) {
        activeRun = value;
    }

    public Timestamp getLeaseUntil() {
        return leaseUntil;
    }

    public void setLeaseUntil(Timestamp value) {
        leaseUntil = value;
    }

    public Timestamp getDueAt() {
        return dueAt;
    }

    public void setDueAt(Timestamp value) {
        dueAt = value;
    }
}
