package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class LogVersion extends BasePageableDAO {

    public static final String TABLE_NAME = "log_version";

    public LogVersion() {
        this.tableName = TABLE_NAME;
        this.pk = "id";
    }

    public boolean deleteByLogIdAndArticleVersion(Integer logId, Integer articleVersion) throws SQLException {
        return execute("delete from " + tableName + " where log_id=? and article_version=?", logId, articleVersion);
    }

    public boolean savePatch(Integer logId, Integer articleVersion, String patchJson, Integer userId, String title, Date createdAt,
                             Integer fromVersion) throws SQLException {
        return execute("insert into " + tableName
                        + " (log_id, article_version, patch_json, user_id, title, created_at, from_version)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                logId, articleVersion, patchJson, userId, title, createdAt, fromVersion);
    }

    public List<Map<String, Object>> findVersionList(Integer logId) throws SQLException {
        return queryListWithParams(
                "select article_version, from_version, created_at, user_id, title from " + tableName
                        + " where log_id=? order by article_version desc",
                logId
        );
    }

    public List<Map<String, Object>> findReversePatchesGreaterThanVersion(Integer logId, Integer targetVersion) throws SQLException {
        return queryListWithParams(
                "select article_version, patch_json from " + tableName
                        + " where log_id=? and article_version>? order by article_version desc",
                logId, targetVersion
        );
    }
}
