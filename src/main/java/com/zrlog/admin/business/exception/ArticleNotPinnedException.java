package com.zrlog.admin.business.exception;

public class ArticleNotPinnedException extends AbstractAdminBusinessException {

    public ArticleNotPinnedException() {
        super(AdminErrorCode.ARTICLE_NOT_PINNED);
    }
}
