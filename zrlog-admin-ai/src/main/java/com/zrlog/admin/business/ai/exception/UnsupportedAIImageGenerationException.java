package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class UnsupportedAIImageGenerationException extends AbstractAdminBusinessException {

    public UnsupportedAIImageGenerationException(String detail) {
        super(AdminErrorCode.AI_IMAGE_GENERATION_UNSUPPORTED, detail);
    }

}
