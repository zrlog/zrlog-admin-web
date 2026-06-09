package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIImageDownloadException extends AbstractAdminBusinessException {

    public AIImageDownloadException(String detail) {
        super("admin.ai.error.imageDownload", detail);
    }

    @Override
    public int getError() {
        return 9036;
    }

}
