package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class PasskeyAuthenticationVerifyRequest implements Validator {

    private String requestId;
    private PasskeyCredential response;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(requestId)) {
            throw new ArgsException("requestId");
        }
        if (Objects.isNull(response) || Objects.isNull(response.getResponse())) {
            throw new ArgsException("response");
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public PasskeyCredential getResponse() {
        return response;
    }

    public void setResponse(PasskeyCredential response) {
        this.response = response;
    }
}
