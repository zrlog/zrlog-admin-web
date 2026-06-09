package com.zrlog.admin.business.exception;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.util.I18nUtil;

public abstract class AbstractAdminBusinessException extends AbstractBusinessException {

    private final String messageKey;
    private final String detail;

    protected AbstractAdminBusinessException(String messageKey) {
        this(messageKey, "");
    }

    protected AbstractAdminBusinessException(String messageKey, String detail) {
        this.messageKey = messageKey;
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        String message = I18nUtil.getAdminBackendStringFromRes(messageKey);
        if (StringUtils.isNotEmpty(detail)) {
            return message + ": " + detail;
        }
        return message;
    }
}
