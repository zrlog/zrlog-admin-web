package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class WebhookConfigRequest implements Validator {

    private Boolean enabled;

    @Override
    public void doValid() {
        if (Objects.isNull(enabled)) {
            throw new ArgsException("enabled");
        }
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
