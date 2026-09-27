package com.zrlog.admin.business.exception;

public class PasskeyRequestBusyException extends AbstractAdminBusinessException {

    public PasskeyRequestBusyException() {
        super(AdminErrorCode.PASSKEY_REQUEST_BUSY);
    }
}
