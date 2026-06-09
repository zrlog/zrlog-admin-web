package com.zrlog.admin.business.exception;

public class StaticHtmlConfigException extends AbstractAdminBusinessException {

    public StaticHtmlConfigException() {
        super("admin.website.blog.validation.staticHostRequired");
    }

    @Override
    public int getError() {
        return 9028;
    }
}
