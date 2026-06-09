package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIMessageSaveException extends AbstractAdminBusinessException {

    public AIMessageSaveException() {
        super("admin.ai.error.messageSave");
    }

    @Override
    public int getError() {
        return 9033;
    }
}
