package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class UnsupportedAIToolException extends AbstractAdminBusinessException {

    public UnsupportedAIToolException(String detail) {
        super(AdminErrorCode.AI_TOOL_UNSUPPORTED, detail);
    }

}
