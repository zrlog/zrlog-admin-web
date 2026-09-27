package com.zrlog.admin.business.exception;

public class PermissionErrorException extends AbstractAdminBusinessException {

    public PermissionErrorException() {
        super(AdminErrorCode.PERMISSION_DENIED);
    }
}
