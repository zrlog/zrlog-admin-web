package com.zrlog.admin.business.exception;

public class ArticleMissingTypeException extends AbstractAdminBusinessException {

    public ArticleMissingTypeException() {
        super(AdminErrorCode.ARTICLE_TYPE_REQUIRED);
    }
}
