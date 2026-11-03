package com.zrlog.admin.data.repository;

import com.zrlog.model.LogVersion;
import com.zrlog.data.repository.BaseRepository;

import com.zrlog.data.repository.*;


import com.hibegin.common.dao.DataSourceWrapper;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class LogVersionRepository extends BaseRepository<LogVersion, Long> {

    private final DataSourceWrapper database;

    public static final String TABLE_NAME = "log_version";

    @Inject
    public LogVersionRepository(DataSourceWrapper database) {
        super(LogVersion.class, database);
        this.database = database;
    }

    public boolean deleteByLogIdAndArticleVersion(Integer logId, Integer articleVersion) throws SQLException {
        return execute("delete from " + tableName + " where log_id=? and article_version=?", logId, articleVersion);
    }

    public boolean savePatch(Integer logId, Integer articleVersion, String patchJson, Integer userId, String title, Date createdAt,
                             Integer fromVersion) throws SQLException {
        LogVersion history = new LogVersion();
        history.setLogId(logId);
        history.setArticleVersion(articleVersion);
        history.setPatchJson(patchJson);
        history.setUserId(userId);
        history.setTitle(title);
        history.setFromVersion(fromVersion);
        new com.hibegin.common.dao.entity.EntityRepository<>(LogVersion.class, database,
                java.time.Clock.fixed(java.time.Instant.ofEpochMilli(createdAt.getTime()), java.time.ZoneOffset.UTC)).insert(history);
        return true;
    }

    public static final class Summary {
        public Integer articleVersion;
        public Integer fromVersion;
        public java.sql.Timestamp createdAt;
        public Integer userId;
        public String title;
    }

    public List<Summary> findVersionList(Integer logId) throws SQLException {
        return queryList("SELECT article_version AS articleVersion,from_version AS fromVersion,created_at AS createdAt,user_id AS userId,title FROM log_version WHERE log_id=? ORDER BY article_version DESC", Summary.class, logId);
    }

    public List<LogVersion> findReversePatchesGreaterThanVersion(Integer logId, Integer targetVersion) throws SQLException {
        return select("WHERE log_id=? AND article_version>? ORDER BY article_version DESC",
                logId, targetVersion
        );
    }
}
