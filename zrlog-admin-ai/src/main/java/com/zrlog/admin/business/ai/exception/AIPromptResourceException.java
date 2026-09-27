package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIPromptResourceException extends AbstractAdminBusinessException {

    public AIPromptResourceException(String detail) {
        super(AdminErrorCode.AI_PROMPT_RESOURCE_UNAVAILABLE, detail);
    }

}
