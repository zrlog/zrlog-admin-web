package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIPromptResourceException extends AbstractAdminBusinessException {

    public AIPromptResourceException(String detail) {
        super("admin.ai.error.promptResource", detail);
    }

    @Override
    public int getError() {
        return 9032;
    }

}
