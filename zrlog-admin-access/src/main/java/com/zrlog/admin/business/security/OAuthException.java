package com.zrlog.admin.business.security;

public final class OAuthException extends com.zrlog.admin.business.exception.AbstractAdminBusinessException {
    private final String error;
    private final int status;
    public OAuthException(String error) { this(error, 400); }
    public OAuthException(String error, int status) { this(error, status, com.zrlog.admin.business.exception.AdminErrorCode.OAUTH_REQUEST_INVALID); }
    public OAuthException(String error, int status, com.zrlog.admin.business.exception.AdminErrorCode message) { super(message, error); this.error = error; this.status = status; }
    public String getOAuthError() { return error; }
    public int getStatus() { return status; }
}
