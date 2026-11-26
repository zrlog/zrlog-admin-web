package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.entity.BaseRepository;
import com.zrlog.admin.data.entity.AssistantNoticeRead;
import jakarta.inject.Inject;

public final class AssistantNoticeReadRepository
        extends BaseRepository<AssistantNoticeRead, String> {
    @Inject
    public AssistantNoticeReadRepository(DataSourceWrapper source) {
        super(AssistantNoticeRead.class, source);
    }

    public static final java.util.List<String> CONTENT_FIELDS =
            java.util.List.of("runId", "userId", "readAt");
}
