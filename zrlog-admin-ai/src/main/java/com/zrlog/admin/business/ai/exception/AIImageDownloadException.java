package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIImageDownloadException extends AbstractAdminBusinessException {

    public AIImageDownloadException(String detail) {
        super(AdminErrorCode.AI_IMAGE_DOWNLOAD_FAILED, detail);
    }

}
