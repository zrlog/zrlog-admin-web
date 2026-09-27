package com.zrlog.admin.business.service;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.UserPasskey;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

public class AdminResourceService {
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminResourceService.class);

    public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
        String lang = I18nUtil.getCurrentLocale();
        AdminResourceInfoResponse response = new AdminResourceInfoResponse();
        PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
        AdminResourceInfoResponse.Capabilities capabilities = new AdminResourceInfoResponse.Capabilities();
        if (Constants.zrLogConfig != null) {
            capabilities.account = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.AccountWebSetup.class) != null;
            capabilities.content = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.ContentWebSetup.class) != null;
            capabilities.assets = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.AssetsWebSetup.class) != null;
            capabilities.ai = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.AIWebSetup.class) != null;
            capabilities.access = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.AccessWebSetup.class) != null;
            capabilities.mcp = Constants.zrLogConfig.getWebSetup(com.zrlog.admin.web.McpWebSetup.class) != null;
        }
        response.setCapabilities(capabilities);
        response.setCurrentVersion(BlogBuildInfoUtil.getBuildId());
        if (Objects.nonNull(publicWebSiteInfo)) {
            response.setWebsiteTitle(ObjectUtil.requireNonNullElse(publicWebSiteInfo.getTitle(), ""));
            response.setAdmin_darkMode(Objects.equals(publicWebSiteInfo.getAdmin_darkMode(), true));
            response.setAdmin_theme(Objects.requireNonNullElse(publicWebSiteInfo.getAdmin_theme(), "default"));
            response.setAdmin_compactMode(Objects.equals(publicWebSiteInfo.getAdmin_compactMode(), true));
            response.setAppId(ObjectUtil.requireNonNullElse(publicWebSiteInfo.getAppId(), ""));
            response.setAdmin_color_primary(ObjectUtil.requireNonNullElse(publicWebSiteInfo.getAdmin_color_primary(), WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR));
        }
        response.setHomeUrl(ZrLogUtil.getHomeUrlWithHost(request));
        response.setArticleRoute("");
        if (ZrLogUtil.isPreviewMode()) {
            AdminResourceInfoResponse.DefaultLoginInfo defaultLoginInfo = new AdminResourceInfoResponse.DefaultLoginInfo();
            defaultLoginInfo.setUserName(System.getenv("DEFAULT_USERNAME"));
            defaultLoginInfo.setPassword(System.getenv("DEFAULT_PASSWORD"));
            defaultLoginInfo.setBackendServerUrl(ObjectUtil.requireNonNullElse(System.getenv("DEFAULT_BACKEND_SERVER_URL"), "/"));
            response.setDefaultLoginInfo(defaultLoginInfo);
        }
        response.setBuildId(BlogBuildInfoUtil.getBuildId());
        response.setLang(lang);
        response.setStaticPage(BaseStaticSitePlugin.isStaticPluginRequest(request));
        //remove
        response.setStaticPlugin(BaseStaticSitePlugin.isStaticPluginRequest(request));
        response.setSupportSse(!EnvKit.isLambda() || EnvKit.isLambdaResponseStreamEnabled());
        response.setAdmin_static_resource_base_url(AdminWebTools.getAdminStaticResourceBaseUrlByWebSite(request));
        FeatureLabWebSiteInfo featureLab = new FeatureLabService().featureLab();
        response.setFeature_webhook_enabled(featureLab.getFeature_webhook_enabled());
        response.setFeature_personal_data_enabled(featureLab.getFeature_personal_data_enabled());
        response.setPasskeyLoginEnabled(isPasskeyLoginEnabled());
        return response;
    }

    private boolean isPasskeyLoginEnabled() {
        try {
            return new UserPasskey().hasAny();
        } catch (SQLException e) {
            LOGGER.warning("Query Passkey login status error, " + e.getMessage());
            return false;
        }
    }

}
