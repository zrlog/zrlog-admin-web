package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIRequestException extends AbstractAdminBusinessException {

    public AIRequestException(String detail) {
        super(AdminErrorCode.AI_REQUEST_FAILED, detail);
    }

}
