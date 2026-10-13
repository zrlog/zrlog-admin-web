package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

/** Safe, localized file validation failures, including failures before SSE starts. */
public final class AIFileInputException extends AbstractAdminBusinessException {
    public AIFileInputException(AdminErrorCode code) { super(code); }
}
