package com.zrlog.admin.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.common.Constants;
import com.zrlog.model.WebSite;
import com.zrlog.plugin.BaseStaticSitePlugin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class AdminWebTools {

    public static void blockUnLoginRequestHandler(HttpRequest request, HttpResponse response) {
        String actionKey = request.getUri();
        if (actionKey.startsWith("/api")) {
            if (!Constants.zrLogConfig.isInstalled()) {
                throw new MissingInstallException();
            }
            throw new AdminAuthException();
        } else {
            String url = AdminConstants.ADMIN_LOGIN_URI_PATH + "?redirectFrom="
                    + URLEncoder.encode(getRequestUriWithQueryString(request), StandardCharsets.UTF_8);
            response.redirect(url);
        }
    }

    private static String getRequestUriWithQueryString(HttpRequest request) {
        String realUri = request.getContextPath() + request.getUri();
        if (request.getUri().endsWith(AdminConstants.ADMIN_URI_BASE_PATH)) {
            realUri = realUri + AdminConstants.INDEX_URI_PATH;
        }
        if (StringUtils.isEmpty(request.getQueryStr())) {
            return realUri;
        }
        return realUri + "?" + request.getQueryStr();
    }

    public static String getAdminStaticResourceBaseUrlByWebSite(HttpRequest request) {
        if (Objects.isNull(Constants.zrLogConfig)) {
            return "";
        }
        if (BaseStaticSitePlugin.isStaticPluginRequest(request) || EnvKit.isDevMode()) {
            return request.getContextPath();
        }
        String websiteHost = new WebSite().getStringValueByName("admin_static_resource_base_url");
        if (Objects.nonNull(websiteHost) && !websiteHost.trim().isEmpty()) {
            return websiteHost + request.getContextPath();
        }
        return request.getContextPath();
    }
}
