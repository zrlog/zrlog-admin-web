package com.zrlog.admin.business.exception;

public class ArticleMissingTitleException extends AbstractAdminBusinessException {

    public ArticleMissingTitleException() {
        super(AdminErrorCode.ARTICLE_TITLE_REQUIRED);
    }
}
