package com.zrlog.admin.business.exception;

public class UpdateArticleExpireException extends AbstractAdminBusinessException {

    public UpdateArticleExpireException() {
        super(AdminErrorCode.ARTICLE_UPDATE_EXPIRED);
    }
}
