package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIRequestException extends AbstractAdminBusinessException {

    public AIRequestException(String detail) {
        super("admin.ai.error.request", detail);
    }

    @Override
    public int getError() {
        return 9030;
    }

}
