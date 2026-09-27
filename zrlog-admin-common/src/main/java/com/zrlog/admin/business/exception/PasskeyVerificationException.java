package com.zrlog.admin.business.exception;

public class PasskeyVerificationException extends AbstractAdminBusinessException {

    public PasskeyVerificationException() {
        super(AdminErrorCode.PASSKEY_REQUEST_INVALID);
    }
}
