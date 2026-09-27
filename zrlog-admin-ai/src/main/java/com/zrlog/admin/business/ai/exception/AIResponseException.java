package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIResponseException extends AbstractAdminBusinessException {

    public AIResponseException(String detail) {
        super(AdminErrorCode.AI_RESPONSE_INVALID, detail);
    }

}
