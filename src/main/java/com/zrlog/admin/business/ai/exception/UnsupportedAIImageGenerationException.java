package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class UnsupportedAIImageGenerationException extends AbstractAdminBusinessException {

    public UnsupportedAIImageGenerationException(String detail) {
        super("admin.ai.error.unsupportedImageGeneration", detail);
    }

    @Override
    public int getError() {
        return 9035;
    }

}
