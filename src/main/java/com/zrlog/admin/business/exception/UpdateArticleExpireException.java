package com.zrlog.admin.business.exception;

public class UpdateArticleExpireException extends AbstractAdminBusinessException {

    public UpdateArticleExpireException() {
        super("admin.article.error.updateExpired");
    }

    @Override
    public int getError() {
        return 9094;
    }
}
