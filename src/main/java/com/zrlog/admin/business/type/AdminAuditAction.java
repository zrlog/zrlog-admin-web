package com.zrlog.admin.business.type;

public enum AdminAuditAction {

    LOGIN_SUCCESS("admin.audit.action.loginSuccess", "login"),
    LOGIN_WITH_PASSKEY("admin.audit.action.loginWithPasskey", "login"),
    CREATE_ARTICLE("admin.audit.action.createArticle", "article"),
    UPDATE_ARTICLE("admin.audit.action.updateArticle", "article"),
    DELETE_ARTICLE("admin.audit.action.deleteArticle", "article"),
    ROLLBACK_ARTICLE_VERSION("admin.audit.action.rollbackArticleVersion", "article"),
    UPDATE_ARTICLE_PINNING("admin.audit.action.updateArticlePinning", "article"),
    UPDATE_SETTING("admin.audit.action.updateSetting", "setting"),
    REPLACE_ARTICLE_RESOURCE_URL("admin.audit.action.replaceArticleResourceUrl", "file-manager"),
    DELETE_FILE("admin.audit.action.deleteFile", "file-manager"),
    RENAME_FILE("admin.audit.action.renameFile", "file-manager"),
    REUPLOAD_MISSING_FILE("admin.audit.action.reuploadMissingFile", "file-manager"),
    CREATE_DIRECTORY("admin.audit.action.createDirectory", "file-manager"),
    UPDATE_PROFILE("admin.audit.action.updateProfile", "security"),
    UPDATE_PASSWORD("admin.audit.action.updatePassword", "security"),
    ENABLE_MFA("admin.audit.action.enableMfa", "security"),
    DISABLE_MFA("admin.audit.action.disableMfa", "security"),
    REGISTER_PASSKEY("admin.audit.action.registerPasskey", "security"),
    REMOVE_PASSKEY("admin.audit.action.removePasskey", "security"),
    UPDATE_WEBHOOK_CONFIG("admin.audit.action.updateWebhookConfig", "security"),
    ROTATE_WEBHOOK_TOKEN("admin.audit.action.rotateWebhookToken", "security"),
    REVOKE_WEBHOOK_TOKEN("admin.audit.action.revokeWebhookToken", "security"),
    EXPORT_PERSONAL_DATA("admin.audit.action.exportPersonalData", "security"),
    EXECUTE_UPGRADE("admin.audit.action.executeUpgrade", "system"),
    REFRESH_CACHE("admin.audit.action.refreshCache", "system"),
    SYNC_ADMIN_STATIC_SITE("admin.audit.action.syncAdminStaticSite", "system"),
    RELEASE_DEV_LOCKS("admin.audit.action.releaseDevLocks", "system"),
    ENABLE_DEV_MODE("admin.audit.action.enableDevMode", "system"),
    DISABLE_DEV_MODE("admin.audit.action.disableDevMode", "system"),
    UPDATE_DASHBOARD_CONFIG("admin.audit.action.updateDashboardConfig", "dashboard"),
    PLUGIN_SURFACE_ACTION("admin.audit.action.pluginSurfaceAction", "plugin"),
    APPLY_TEMPLATE("admin.audit.action.applyTemplate", "template"),
    UPDATE_TEMPLATE_CONFIG("admin.audit.action.updateTemplateConfig", "template"),
    UPLOAD_TEMPLATE("admin.audit.action.uploadTemplate", "template"),
    DELETE_TEMPLATE("admin.audit.action.deleteTemplate", "template");

    private final String i18nKey;
    private final String type;

    AdminAuditAction(String i18nKey, String type) {
        this.i18nKey = i18nKey;
        this.type = type;
    }

    public String getI18nKey() {
        return i18nKey;
    }

    public String getType() {
        return type;
    }
}
