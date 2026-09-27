package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class FirstUseChecklistRequest implements Validator {

    private Integer version;

    @Override
    public void doValid() {
        if (!Integer.valueOf(1).equals(version)) {
            throw new ArgsException("version");
        }
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
