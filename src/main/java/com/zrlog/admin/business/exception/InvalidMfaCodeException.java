package com.zrlog.admin.business.exception;

public class InvalidMfaCodeException extends AbstractAdminBusinessException {

    public InvalidMfaCodeException() {
        super("admin.mfa.error.codeInvalid");
    }

    @Override
    public int getError() {
        return 9015;
    }
}
