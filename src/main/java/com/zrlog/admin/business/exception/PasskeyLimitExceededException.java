package com.zrlog.admin.business.exception;

public class PasskeyLimitExceededException extends AbstractAdminBusinessException {

    public PasskeyLimitExceededException() {
        super(AdminErrorCode.PASSKEY_LIMIT_EXCEEDED);
    }
}
