package com.zrlog.admin.business.service;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.response.VersionResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.model.WebSite;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.Map;

public class WebSiteSettingsService {

    public VersionResponse version(Map<String, Object> backendMessages) {
        return version(UpdateVersionInfoPlugin.getCurrentChangeLog(backendMessages));
    }

    public VersionResponse version(String changelog) {
        VersionResponse response = new VersionResponse();
        response.setBuildId(BlogBuildInfoUtil.getBuildId());
        response.setVersion(BlogBuildInfoUtil.getVersion());
        response.setChangelog(changelog);
        try (InputStream input = WebSiteSettingsService.class.getResourceAsStream(AdminConstants.BUILD_SYSTEM_INFO_MD)) {
            response.setBuildSystemInfo(input == null
                    ? "#### Not find build system info file"
                    : IOUtil.getStringInputStream(input));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read build system information", e);
        }
        return response;
    }

    public void update(Object settings, HttpRequest request) throws SQLException {
        new AdminAuditService().record(request, AdminAuditAction.UPDATE_SETTING,
                settingAuditContent(settings, request.getUri()));
        Map<String, Object> values = BeanUtil.convert(settings, Map.class);
        if (values != null) {
            WebSite webSite = new WebSite();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                webSite.updateByKV(entry.getKey(), toWebsiteValue(entry.getValue()));
            }
        }
        BaseDataInitVO initData = Constants.zrLogConfig.getCacheService().getInitData();
        if (initData != null) {
            initData.setWebSite(new WebSite().getPublicWebSite());
        }
    }

    public void updateAdmin(AdminWebSiteInfo settings, HttpRequest request) throws SQLException {
        // Older clients omit the new field; only an explicit empty string clears it.
        if (settings.getBackend_server_url() == null) {
            settings.setBackend_server_url(new WebSite().getStringValueByName(com.zrlog.admin.util.BackendServerUrl.SETTING_KEY));
        }
        update(settings, request);
        Constants.zrLogConfig.getTokenService().updateSessionTimeout(settings.getSession_timeout());
    }

    public void updateUpgrade(UpgradeWebSiteInfo settings, HttpRequest request) throws SQLException {
        UpdateVersionInfoPlugin plugin = Constants.zrLogConfig.getPlugin(UpdateVersionInfoPlugin.class);
        if (plugin == null) {
            throw new IllegalStateException(
                    I18nUtil.getAdminBackendStringFromRes("admin.website.upgrade.error.missingUpdatePlugin"));
        }
        update(settings, request);
        plugin.stop();
        if (AutoUpgradeVersionType.cycle(settings.getAutoUpgradeVersion()) != AutoUpgradeVersionType.NEVER) {
            plugin.start();
        }
    }

    private String settingAuditContent(Object settings, String uri) {
        String key = AdminConstants.TITLE_MAP.get(adminPageUri(uri));
        String content = I18nUtil.getAdminBackendStringFromRes(key);
        return StringUtils.isEmpty(content) ? settings.getClass().getSimpleName() : content;
    }

    private String adminPageUri(String uri) {
        String apiPrefix = "/api" + AdminConstants.ADMIN_URI_BASE_PATH;
        return uri.startsWith(apiPrefix)
                ? AdminConstants.ADMIN_URI_BASE_PATH + uri.substring(apiPrefix.length())
                : uri;
    }

    private Object toWebsiteValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number) && Math.rint(number) == number) {
                return Long.toString((long) number);
            }
        }
        return value.toString();
    }
}
