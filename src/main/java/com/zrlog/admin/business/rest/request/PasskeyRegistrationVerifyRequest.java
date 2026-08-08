package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.Objects;

public class PasskeyRegistrationVerifyRequest implements Validator {

    private String requestId;
    private PasskeyCredential response;
    private String name;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(requestId)) {
            throw new ArgsException("requestId");
        }
        if (Objects.isNull(response) || Objects.isNull(response.getResponse())) {
            throw new ArgsException("response");
        }
        if (StringUtils.isNotEmpty(name) && name.length() > 64) {
            throw new ArgsException("name");
        }
    }

    @Override
    public void doClean() {
        if (StringUtils.isNotEmpty(name)) {
            name = Jsoup.clean(name, Safelist.none()).trim();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
