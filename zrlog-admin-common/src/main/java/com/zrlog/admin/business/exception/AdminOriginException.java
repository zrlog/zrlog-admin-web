package com.zrlog.admin.business.exception;

/** Invalid browser origin. Retains the error contract used by existing member APIs. */
public final class AdminOriginException extends AbstractAdminBusinessException {
    public AdminOriginException() {
        super(AdminErrorCode.OAUTH_REQUEST_INVALID, "access_denied");
    }
}
