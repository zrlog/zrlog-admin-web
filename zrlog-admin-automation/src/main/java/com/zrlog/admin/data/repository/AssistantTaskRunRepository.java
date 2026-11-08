package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.data.entity.AssistantTaskRun;
import com.zrlog.data.repository.BaseRepository;
import jakarta.inject.Inject;

public final class AssistantTaskRunRepository extends BaseRepository<AssistantTaskRun, String> {
    @Inject
    public AssistantTaskRunRepository(DataSourceWrapper source) {
        super(AssistantTaskRun.class, source);
    }

    public static final java.util.List<String> CONTENT_FIELDS =
            java.util.List.of(
                    "taskId",
                    "ownerId",
                    "taskName",
                    "taskNameKey",
                    "taskType",
                    "taskRevision",
                    "scheduledAt",
                    "startedAt",
                    "finishedAt",
                    "trigger",
                    "triggeredBy",
                    "executionUserId",
                    "status",
                    "errorCode",
                    "notifyOwner",
                    "summary",
                    "planSnapshot",
                    "result");
}
