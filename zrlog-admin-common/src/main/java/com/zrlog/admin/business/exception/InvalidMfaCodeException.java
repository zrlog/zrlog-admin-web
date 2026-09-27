package com.zrlog.admin.business.exception;

public class InvalidMfaCodeException extends AbstractAdminBusinessException {

    public InvalidMfaCodeException() {
        super(AdminErrorCode.MFA_CODE_INVALID);
    }
}
