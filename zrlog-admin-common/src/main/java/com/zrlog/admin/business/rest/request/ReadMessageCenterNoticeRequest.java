package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class ReadMessageCenterNoticeRequest implements Validator {

    private String taskKey;
    private String source;
    private String referenceId;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(taskKey)) {
            throw new ArgsException("taskKey");
        }
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getSource() {
        return source;
    }

    public String getReferenceId() {
        return referenceId;
    }
}
