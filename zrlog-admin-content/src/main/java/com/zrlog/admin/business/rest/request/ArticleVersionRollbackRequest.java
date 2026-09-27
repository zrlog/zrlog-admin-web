package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ArticleVersionRollbackRequest implements Validator {

    private Integer logId;
    private Integer version;
    private Integer targetVersion;

    public Integer getLogId() {
        return logId;
    }

    public void setLogId(Integer logId) {
        this.logId = logId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(Integer targetVersion) {
        this.targetVersion = targetVersion;
    }

    @Override
    public void doValid() {
        if (logId == null || version == null || targetVersion == null) {
            throw new ArgsException("logId/version/targetVersion");
        }
    }
}
