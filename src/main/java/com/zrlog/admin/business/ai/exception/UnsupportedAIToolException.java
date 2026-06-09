package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class UnsupportedAIToolException extends AbstractAdminBusinessException {

    public UnsupportedAIToolException(String detail) {
        super("admin.ai.error.unsupportedTool", detail);
    }

    @Override
    public int getError() {
        return 9034;
    }

}
