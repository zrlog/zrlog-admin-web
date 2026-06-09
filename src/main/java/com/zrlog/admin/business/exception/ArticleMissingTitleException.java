package com.zrlog.admin.business.exception;

public class ArticleMissingTitleException extends AbstractAdminBusinessException {

    public ArticleMissingTitleException() {
        super("admin.article.validation.titleRequired");
    }

    @Override
    public int getError() {
        return 9026;
    }
}
