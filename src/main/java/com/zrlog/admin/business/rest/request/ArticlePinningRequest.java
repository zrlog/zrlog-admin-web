package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ArticlePinningRequest implements Validator {

    private Long logId;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    @Override
    public void doValid() {
        if (logId == null || logId <= 0) {
            throw new ArgsException("logId");
        }
    }
}
