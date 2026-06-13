package com.zrlog.admin.business.exception;

public class AdminAuthException extends AbstractAdminBusinessException {

    public AdminAuthException() {
        super(AdminErrorCode.AUTH_SESSION_EXPIRED);
    }
}
