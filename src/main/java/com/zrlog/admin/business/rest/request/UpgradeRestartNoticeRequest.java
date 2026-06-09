package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Set;

public class UpgradeRestartNoticeRequest implements Validator {

    private static final Set<String> STATUSES = Set.of("success", "warning", "error");

    private String status;
    private String buildId;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(status) || !STATUSES.contains(status)) {
            throw new ArgsException("status");
        }
    }

    @Override
    public void doClean() {
        status = clean(status);
        buildId = clean(buildId);
    }

    private String clean(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        return value.trim();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }
}
