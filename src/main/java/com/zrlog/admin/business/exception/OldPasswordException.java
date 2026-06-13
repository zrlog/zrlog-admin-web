package com.zrlog.admin.business.exception;

public class OldPasswordException extends AbstractAdminBusinessException {

    public OldPasswordException() {
        super(AdminErrorCode.USER_OLD_PASSWORD_INVALID);
    }
}
