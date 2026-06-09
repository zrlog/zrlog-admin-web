package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIResponseException extends AbstractAdminBusinessException {

    public AIResponseException(String detail) {
        super("admin.ai.error.response", detail);
    }

    @Override
    public int getError() {
        return 9031;
    }

}
