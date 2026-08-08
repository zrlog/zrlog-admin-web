package com.zrlog.admin.business.rest.response;

public class AdminResourceInfoResponse {

    private String currentVersion;
    private String websiteTitle;
    private Boolean admin_darkMode;
    private String admin_theme;
    private Boolean admin_compactMode;
    private String appId;
    private String admin_color_primary;
    private String homeUrl;
    private String articleRoute;
    private DefaultLoginInfo defaultLoginInfo;
    private String buildId;
    private String lang;
    private Boolean staticPage;
    private Boolean staticPlugin;
    private Boolean supportSse;
    private String admin_static_resource_base_url;
    private Boolean feature_webhook_enabled;
    private Boolean feature_personal_data_enabled;
    private Boolean passkeyLoginEnabled;

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getWebsiteTitle() {
        return websiteTitle;
    }

    public void setWebsiteTitle(String websiteTitle) {
        this.websiteTitle = websiteTitle;
    }

    public Boolean getAdmin_darkMode() {
        return admin_darkMode;
    }

    public void setAdmin_darkMode(Boolean admin_darkMode) {
        this.admin_darkMode = admin_darkMode;
    }

    public String getAdmin_theme() {
        return admin_theme;
    }

    public void setAdmin_theme(String admin_theme) {
        this.admin_theme = admin_theme;
    }

    public Boolean getAdmin_compactMode() {
        return admin_compactMode;
    }

    public void setAdmin_compactMode(Boolean admin_compactMode) {
        this.admin_compactMode = admin_compactMode;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAdmin_color_primary() {
        return admin_color_primary;
    }

    public void setAdmin_color_primary(String admin_color_primary) {
        this.admin_color_primary = admin_color_primary;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public void setHomeUrl(String homeUrl) {
        this.homeUrl = homeUrl;
    }

    public String getArticleRoute() {
        return articleRoute;
    }

    public void setArticleRoute(String articleRoute) {
        this.articleRoute = articleRoute;
    }

    public DefaultLoginInfo getDefaultLoginInfo() {
        return defaultLoginInfo;
    }

    public void setDefaultLoginInfo(DefaultLoginInfo defaultLoginInfo) {
        this.defaultLoginInfo = defaultLoginInfo;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Boolean getStaticPage() {
        return staticPage;
    }

    public void setStaticPage(Boolean staticPage) {
        this.staticPage = staticPage;
    }

    public Boolean getStaticPlugin() {
        return staticPlugin;
    }

    public void setStaticPlugin(Boolean staticPlugin) {
        this.staticPlugin = staticPlugin;
    }

    public Boolean getSupportSse() {
        return supportSse;
    }

    public void setSupportSse(Boolean supportSse) {
        this.supportSse = supportSse;
    }

    public String getAdmin_static_resource_base_url() {
        return admin_static_resource_base_url;
    }

    public void setAdmin_static_resource_base_url(String admin_static_resource_base_url) {
        this.admin_static_resource_base_url = admin_static_resource_base_url;
    }

    public Boolean getFeature_webhook_enabled() {
        return feature_webhook_enabled;
    }

    public void setFeature_webhook_enabled(Boolean feature_webhook_enabled) {
        this.feature_webhook_enabled = feature_webhook_enabled;
    }

    public Boolean getFeature_personal_data_enabled() {
        return feature_personal_data_enabled;
    }

    public void setFeature_personal_data_enabled(Boolean feature_personal_data_enabled) {
        this.feature_personal_data_enabled = feature_personal_data_enabled;
    }

    public Boolean getPasskeyLoginEnabled() {
        return passkeyLoginEnabled;
    }

    public void setPasskeyLoginEnabled(Boolean passkeyLoginEnabled) {
        this.passkeyLoginEnabled = passkeyLoginEnabled;
    }

    public static class DefaultLoginInfo {

        private String userName;
        private String password;
        private String backendServerUrl;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getBackendServerUrl() {
            return backendServerUrl;
        }

        public void setBackendServerUrl(String backendServerUrl) {
            this.backendServerUrl = backendServerUrl;
        }
    }
}
