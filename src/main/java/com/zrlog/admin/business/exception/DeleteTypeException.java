package com.zrlog.admin.business.exception;

public class DeleteTypeException extends AbstractAdminBusinessException {

    public DeleteTypeException() {
        super("admin.articleType.error.deleteHasArticle");
    }

    @Override
    public int getError() {
        return 9025;
    }
}
