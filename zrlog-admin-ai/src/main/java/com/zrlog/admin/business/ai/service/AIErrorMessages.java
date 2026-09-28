package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.util.I18nUtil;
import java.net.http.HttpTimeoutException;

/** Translate failures at the AI response boundary; provider diagnostics are not UI copy. */
public final class AIErrorMessages {
    private AIErrorMessages() { }

    public static String message(Throwable failure, String fallbackKey) {
        if (failure instanceof AbstractAdminBusinessException) {
            return ((AbstractAdminBusinessException) failure).getUserMessage();
        }
        if (failure instanceof ArgsException) {
            return I18nUtil.getAdminBackendStringFromRes("admin.ai.error.configuration");
        }
        if (failure instanceof HttpTimeoutException) {
            return I18nUtil.getAdminBackendStringFromRes("admin.ai.error.timeout");
        }
        return I18nUtil.getAdminBackendStringFromRes(fallbackKey);
    }
}
