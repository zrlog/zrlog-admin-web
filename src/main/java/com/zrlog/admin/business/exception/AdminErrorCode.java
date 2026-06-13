package com.zrlog.admin.business.exception;

public enum AdminErrorCode {

    AUTH_SESSION_EXPIRED(9001, "ADMIN_AUTH_SESSION_EXPIRED", "admin.session.timeout"),
    LOGIN_USERNAME_PASSWORD_REQUIRED(9009, "ADMIN_LOGIN_USERNAME_PASSWORD_REQUIRED", "admin.login.validation.usernameAndPasswordRequired"),
    LOGIN_USERNAME_PASSWORD_INVALID(9010, "ADMIN_LOGIN_USERNAME_PASSWORD_INVALID", "admin.login.error.usernameOrPassword"),
    USER_OLD_PASSWORD_INVALID(9013, "ADMIN_USER_OLD_PASSWORD_INVALID", "admin.user.password.error.oldPassword"),
    MFA_CODE_REQUIRED(9014, "ADMIN_MFA_CODE_REQUIRED", "admin.mfa.validation.codeRequired"),
    MFA_CODE_INVALID(9015, "ADMIN_MFA_CODE_INVALID", "admin.mfa.error.codeInvalid"),
    PERMISSION_DENIED(9016, "ADMIN_PERMISSION_DENIED", "admin.permission.error"),
    ARTICLE_TYPE_DELETE_HAS_ARTICLE(9025, "ADMIN_ARTICLE_TYPE_DELETE_HAS_ARTICLE", "admin.articleType.error.deleteHasArticle"),
    ARTICLE_TITLE_REQUIRED(9026, "ADMIN_ARTICLE_TITLE_REQUIRED", "admin.article.validation.titleRequired"),
    ARTICLE_TYPE_REQUIRED(9027, "ADMIN_ARTICLE_TYPE_REQUIRED", "admin.article.validation.typeRequired"),
    WEBSITE_STATIC_HOST_REQUIRED(9028, "ADMIN_WEBSITE_STATIC_HOST_REQUIRED", "admin.website.blog.validation.staticHostRequired"),
    AI_REQUEST_FAILED(9030, "ADMIN_AI_REQUEST_FAILED", "admin.ai.error.request"),
    AI_RESPONSE_INVALID(9031, "ADMIN_AI_RESPONSE_INVALID", "admin.ai.error.response"),
    AI_PROMPT_RESOURCE_UNAVAILABLE(9032, "ADMIN_AI_PROMPT_RESOURCE_UNAVAILABLE", "admin.ai.error.promptResource"),
    AI_MESSAGE_SAVE_FAILED(9033, "ADMIN_AI_MESSAGE_SAVE_FAILED", "admin.ai.error.messageSave"),
    AI_TOOL_UNSUPPORTED(9034, "ADMIN_AI_TOOL_UNSUPPORTED", "admin.ai.error.unsupportedTool"),
    AI_IMAGE_GENERATION_UNSUPPORTED(9035, "ADMIN_AI_IMAGE_GENERATION_UNSUPPORTED", "admin.ai.error.unsupportedImageGeneration"),
    AI_IMAGE_DOWNLOAD_FAILED(9036, "ADMIN_AI_IMAGE_DOWNLOAD_FAILED", "admin.ai.error.imageDownload"),
    AI_RESPONSE_INCOMPLETE(9037, "ADMIN_AI_RESPONSE_INCOMPLETE", "admin.ai.error.incomplete"),
    ARTICLE_UPDATE_EXPIRED(9094, "ADMIN_ARTICLE_UPDATE_EXPIRED", "admin.article.error.updateExpired");

    private final int legacyCode;
    private final String code;
    private final String messageKey;

    AdminErrorCode(int legacyCode, String code, String messageKey) {
        this.legacyCode = legacyCode;
        this.code = code;
        this.messageKey = messageKey;
    }

    public int getLegacyCode() {
        return legacyCode;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
