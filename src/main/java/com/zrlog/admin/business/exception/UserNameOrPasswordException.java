package com.zrlog.admin.business.exception;

public class UserNameOrPasswordException extends AbstractAdminBusinessException {

    public UserNameOrPasswordException() {
        super(AdminErrorCode.LOGIN_USERNAME_PASSWORD_INVALID);
    }
}
