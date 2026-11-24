package com.zrlog.admin.business.ai.service;

import com.zrlog.util.I18nUtil;

/** Checkpoints store stable codes; responses translate them in the current account language. */
final class AIChatErrorMessages {
    private AIChatErrorMessages() {}

    static String message(String code) {
        return message(code, null);
    }

    static String message(String code, String status) {
        if (code == null || code.isBlank()) return null;
        String key;
        switch (code) {
            case "permission":
                key = "admin.ai.chat.error.permission";
                break;
            case "saveFailed":
                key =
                        "uncertain".equals(status)
                                ? "admin.ai.chat.error.writeUnconfirmed"
                                : "admin.ai.chat.error.save";
                break;
            case "categoryLookupFailed":
                key = "admin.ai.chat.error.categoryLookup";
                break;
            case "requestTimeout":
                key = "admin.ai.error.timeout";
                break;
            case "responseIncomplete":
                key = "admin.ai.error.incomplete";
                break;
            case "providerRequestFailed":
                key = "admin.ai.error.request";
                break;
            case "providerResponseInvalid":
                key = "admin.ai.error.response";
                break;
            case "contextChanged":
                key = "admin.ai.chat.validation.contextChanged";
                break;
            case "fileInputFailed":
                key = "admin.ai.files.unavailable";
                break;
            default:
                key = "admin.ai.chat.error.request";
        }
        return I18nUtil.getAdminBackendStringFromRes(key);
    }
}
