package com.zrlog.admin.business.exception;

public class AdminAuthException extends AbstractAdminBusinessException {

    public AdminAuthException() {
        super("admin.session.timeout");
    }

    @Override
    public int getError() {
        return 9001;
    }
}
