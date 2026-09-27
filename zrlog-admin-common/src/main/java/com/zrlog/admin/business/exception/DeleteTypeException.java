package com.zrlog.admin.business.exception;

public class DeleteTypeException extends AbstractAdminBusinessException {

    public DeleteTypeException() {
        super(AdminErrorCode.ARTICLE_TYPE_DELETE_HAS_ARTICLE);
    }
}
