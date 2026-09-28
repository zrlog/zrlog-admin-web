package com.zrlog.admin.business.exception;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.util.I18nUtil;

public abstract class AbstractAdminBusinessException extends AbstractBusinessException {

    private final AdminErrorCode errorCode;
    private final String detail;

    protected AbstractAdminBusinessException(AdminErrorCode errorCode) {
        this(errorCode, "");
    }

    protected AbstractAdminBusinessException(AdminErrorCode errorCode, String detail) {
        this.errorCode = errorCode;
        this.detail = detail;
    }

    @Override
    public int getError() {
        return errorCode.getLegacyCode();
    }

    public String getErrorCode() {
        return errorCode.getCode();
    }

    @Override
    public String getMessage() {
        String message = getUserMessage();
        if (StringUtils.isNotEmpty(detail)) {
            return message + ": " + detail;
        }
        return message;
    }

    /** Localized display text, without diagnostic details supplied by the caller. */
    public String getUserMessage() {
        return I18nUtil.getAdminBackendStringFromRes(errorCode.getMessageKey());
    }
}
