package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIMessageSaveException extends AbstractAdminBusinessException {

    public AIMessageSaveException() {
        super(AdminErrorCode.AI_MESSAGE_SAVE_FAILED);
    }
}
