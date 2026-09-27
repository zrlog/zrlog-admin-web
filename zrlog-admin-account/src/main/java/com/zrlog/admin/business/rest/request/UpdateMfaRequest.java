package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class UpdateMfaRequest implements Validator {

    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public void doValid() {
        if (Objects.isNull(code) || code.trim().isEmpty()) {
            throw new ArgsException("code");
        }
    }
}
