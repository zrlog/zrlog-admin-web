package com.zrlog.admin.business.exception;

public class UserNameOrPasswordException extends AbstractAdminBusinessException {

    public UserNameOrPasswordException() {
        super("admin.login.error.usernameOrPassword");
    }

    @Override
    public int getError() {
        return 9010;
    }
}
