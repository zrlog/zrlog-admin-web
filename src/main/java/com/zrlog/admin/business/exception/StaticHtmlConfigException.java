package com.zrlog.admin.business.exception;

public class StaticHtmlConfigException extends AbstractAdminBusinessException {

    public StaticHtmlConfigException() {
        super(AdminErrorCode.WEBSITE_STATIC_HOST_REQUIRED);
    }
}
