package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.exception.OldPasswordException;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class PasskeyRemoveRequest implements Validator {

    private Long id;
    private String password;
    private String mfaCode;

    @Override
    public void doValid() {
        if (Objects.isNull(id) || id <= 0) {
            throw new ArgsException("id");
        }
        if (StringUtils.isEmpty(password)) {
            throw new OldPasswordException();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMfaCode() {
        return mfaCode;
    }

    public void setMfaCode(String mfaCode) {
        this.mfaCode = mfaCode;
    }
}
