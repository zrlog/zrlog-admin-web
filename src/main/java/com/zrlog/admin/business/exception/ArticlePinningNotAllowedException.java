package com.zrlog.admin.business.exception;

public class ArticlePinningNotAllowedException extends AbstractAdminBusinessException {

    public ArticlePinningNotAllowedException() {
        super(AdminErrorCode.ARTICLE_PINNING_NOT_ALLOWED);
    }
}
