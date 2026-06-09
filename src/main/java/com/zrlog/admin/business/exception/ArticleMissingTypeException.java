package com.zrlog.admin.business.exception;

public class ArticleMissingTypeException extends AbstractAdminBusinessException {

    public ArticleMissingTypeException() {
        super("admin.article.validation.typeRequired");
    }

    @Override
    public int getError() {
        return 9027;
    }
}
