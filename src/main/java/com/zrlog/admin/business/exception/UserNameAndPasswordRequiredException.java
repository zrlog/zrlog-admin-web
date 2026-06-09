package com.zrlog.admin.business.exception;

public class UserNameAndPasswordRequiredException extends AbstractAdminBusinessException {

    public UserNameAndPasswordRequiredException() {
        super("admin.login.validation.usernameAndPasswordRequired");
    }

    @Override
    public int getError() {
        return 9009;
    }
}
