package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.data.entity.AssistantTask;
import com.zrlog.data.repository.BaseRepository;
import jakarta.inject.Inject;

public final class AssistantTaskRepository extends BaseRepository<AssistantTask, String> {
    @Inject
    public AssistantTaskRepository(DataSourceWrapper source) {
        super(AssistantTask.class, source);
    }

    public static final java.util.List<String> CONTENT_FIELDS =
            java.util.List.of(
                    "ownerId",
                    "name",
                    "nameKey",
                    "type",
                    "enabled",
                    "revision",
                    "updatedBy",
                    "schedule",
                    "parameters",
                    "executionActions",
                    "nextRunAt",
                    "lastRunAt",
                    "lastStatus",
                    "activeRunId",
                    "activeRun",
                    "leaseUntil",
                    "dueAt");
}
