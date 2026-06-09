package com.zrlog.admin.business.exception;

public class PermissionErrorException extends AbstractAdminBusinessException {

    public PermissionErrorException() {
        super("admin.permission.error");
    }

    @Override
    public int getError() {
        return 9013;
    }
}
