package com.zrlog.admin.util;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.util.I18nUtil;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** User-facing errors never include diagnostics supplied by an exception or remote service. */
public final class AdminErrorMessages {
    private AdminErrorMessages() { }

    public static String message(Throwable failure, String fallbackKey) {
        Throwable cause = unwrap(failure);
        return cause instanceof AbstractAdminBusinessException
                ? ((AbstractAdminBusinessException) cause).getUserMessage()
                : I18nUtil.getAdminBackendStringFromRes(fallbackKey);
    }

    static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null && failure.getCause() != failure) {
            failure = failure.getCause();
        }
        return failure;
    }
}
