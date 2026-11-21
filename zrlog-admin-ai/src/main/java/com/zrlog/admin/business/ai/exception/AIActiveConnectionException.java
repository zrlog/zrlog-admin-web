package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public final class AIActiveConnectionException extends AbstractAdminBusinessException {
    public AIActiveConnectionException() {
        super(AdminErrorCode.AI_ACTIVE_CONNECTION);
    }
}
