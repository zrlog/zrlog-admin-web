package com.zrlog.admin.business.exception;

public class MfaCodeRequiredException extends AbstractAdminBusinessException {

    public MfaCodeRequiredException() {
        super("admin.mfa.validation.codeRequired");
    }

    @Override
    public int getError() {
        return 9014;
    }
}
