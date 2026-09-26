package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIRequestException extends AbstractAdminBusinessException {
    private final Integer statusCode;

    public AIRequestException(String detail) {
        this(detail, null);
    }

    public AIRequestException(String detail, Integer statusCode) {
        super(AdminErrorCode.AI_REQUEST_FAILED, detail);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() { return statusCode; }

}
