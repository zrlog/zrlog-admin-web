package com.zrlog.admin.business.exception;

public class OldPasswordException extends AbstractAdminBusinessException {

    public OldPasswordException() {
        super("admin.user.password.error.oldPassword");
    }

    @Override
    public int getError() {
        return 9013;
    }
}
