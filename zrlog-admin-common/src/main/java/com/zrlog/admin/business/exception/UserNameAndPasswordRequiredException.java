package com.zrlog.admin.business.exception;

public class UserNameAndPasswordRequiredException extends AbstractAdminBusinessException {

    public UserNameAndPasswordRequiredException() {
        super(AdminErrorCode.LOGIN_USERNAME_PASSWORD_REQUIRED);
    }
}
