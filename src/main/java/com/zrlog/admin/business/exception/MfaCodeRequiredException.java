package com.zrlog.admin.business.exception;

public class MfaCodeRequiredException extends AbstractAdminBusinessException {

    public MfaCodeRequiredException() {
        super(AdminErrorCode.MFA_CODE_REQUIRED);
    }
}
