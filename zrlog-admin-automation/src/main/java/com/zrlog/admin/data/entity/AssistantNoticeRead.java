package com.zrlog.admin.data.entity;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Entity;
import java.sql.Timestamp;

@Entity(table = "assistant_notice_read", idColumn = "id")
public final class AssistantNoticeRead extends BaseEntity<String> {
    private String runId;
    private Integer userId;
    private Timestamp readAt;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String value) {
        runId = value;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer value) {
        userId = value;
    }

    public Timestamp getReadAt() {
        return readAt;
    }

    public void setReadAt(Timestamp value) {
        readAt = value;
    }
}
