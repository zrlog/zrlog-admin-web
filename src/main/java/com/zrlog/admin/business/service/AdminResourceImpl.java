package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.*;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.util.ManifestUtils;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.UserPasskey;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AdminResourceImpl implements AdminResource {

    private static final Logger LOGGER = LoggerUtil.getLogger(AdminResourceImpl.class);

    private final Set<String> pageUris;
    private final Set<String> staticUris;
    private final Set<String> apiUris;
    private final long fileBuildId;
    private final String basePath;
    private final String contextPath;

    public AdminResourceImpl(String contextPath) {
        this.basePath = contextPath + "/";
        this.contextPath = contextPath;
        this.pageUris = wrapperUris(getUris("/conf/pwa-page.txt"));
        this.apiUris = getUris("/conf/pwa-api.txt");
        this.staticUris = getStaticUri();
        Map<String, Object> resourceMap = new TreeMap<>();
        resourceMap.put("uris", pageUris);
        resourceMap.put("static", staticUris);
        resourceMap.put("documentTitleMap", AdminConstants.TITLE_MAP);
        try {
            resourceMap.put("manifestIcons", ManifestUtils.getManifestIcons());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.fileBuildId = Math.abs(SecurityUtils.md5(new Gson().toJson(resourceMap)).hashCode());
    }

    private Set<String> getStaticUri() {
        InputStream resourceAsStream = AdminResourceImpl.class.getResourceAsStream(ADMIN_ASSET_MANIFEST_JSON);
        Set<String> cacheUris = new LinkedHashSet<>();
        if (Objects.isNull(resourceAsStream)) {
            return cacheUris;
        }
        String str = IOUtil.getStringInputStream(resourceAsStream);
        if (StringUtils.isNotEmpty(str)) {
            Map<String, Object> map = new Gson().fromJson(str, new TypeToken<>() {
            });
            Map<String, Object> staticFiles = (Map<String, Object>) map.get("files");
            if (Objects.nonNull(staticFiles)) {
                cacheUris.addAll(staticFiles.values().stream().filter(e -> !((String) e).endsWith(".html")).map(String::valueOf).collect(Collectors.toList()));
                cacheUris = new LinkedHashSet<>(wrapperUris(cacheUris));
            }
        }
        cacheUris.addAll(wrapperUris(getUris(AdminConstants.ADMIN_URI_BASE_PATH + "/pwa-resource.txt")));
        return cacheUris;
    }


    private List<String> buildRealPageUrls(String e, String adminResourceUrl, HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        String[] split = e.split("\\?");
        if (split.length == 1) {
            sb.append(adminResourceUrl).append(e);
        } else {
            sb.append(adminResourceUrl).append(split[0]);
        }
        if (e.endsWith("/?pwa=true") || e.endsWith("/")) {
            sb.append("?");
        } else {
            sb.append(BaseStaticSitePlugin.isStaticPluginRequest(request) ? ".html?" : "?");
        }
        if (e.contains("?pwa=true")) {
            sb.append(split[1]);
        } else {
            sb.append("v=").append(fileBuildId);
            if (split.length > 1) {
                sb.append("&");
                sb.append(split[1]);
            }
        }
        return Collections.singletonList(sb.toString());
    }

    @Override
    public ByteArrayInputStream renderServiceWorker(HttpRequest request) {
        Set<String> realUris = new LinkedHashSet<>();
        String adminResourceUrl = AdminWebTools.getAdminStaticResourceBaseUrlByWebSite(request);
        String withoutContextPath = adminResourceUrl.substring(0, adminResourceUrl.length() - contextPath.length());
        pageUris.forEach(uri -> realUris.addAll(buildRealPageUrls(uri, withoutContextPath, request)));
        staticUris.forEach(uri -> realUris.add(withoutContextPath + uri));
        String newUrls = "const urlsToCache = " + new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(realUris);
        return new ByteArrayInputStream(IOUtil.getStringInputStream(AdminResourceImpl.class.getResourceAsStream(AdminConstants.ADMIN_SERVICE_WORKER_JS)).replace("const urlsToCache = []", newUrls).getBytes());
    }

    @Override
    public String getStaticResourceBuildId() {
        CacheService cacheService = Constants.zrLogConfig.getCacheService();
        if (Objects.isNull(cacheService)) {
            return Math.abs(fileBuildId) + "";
        }
        return Math.abs(fileBuildId + cacheService.getWebSiteVersion()) + "";
    }

    private Set<String> getUris(String resourceName) {
        InputStream textIn = AdminResourceImpl.class.getResourceAsStream(resourceName);
        if (Objects.isNull(textIn)) {
            return new HashSet<>();
        }
        String strVendors = IOUtil.getStringInputStream(textIn);
        return new LinkedHashSet<>(Arrays.asList(strVendors.split("\n")));
    }


    private Set<String> wrapperUris(Set<String> uris) {
        Set<String> cacheUris = new LinkedHashSet<>();
        uris.forEach(file -> {
            if (file.startsWith(AdminConstants.ADMIN_URI_BASE_PATH)) {
                cacheUris.add((basePath + file.substring(1)));
            } else if (file.startsWith("admin/")) {
                cacheUris.add((basePath + file));
            } else {
                //vendors
                cacheUris.add(new File((basePath + AdminConstants.ADMIN_URI_BASE_PATH + file)).toString());
            }
        });
        return cacheUris;
    }

    @Override
    public Set<String> getAdminStaticResourceUris() {
        return staticUris;
    }

    @Override
    public Set<String> getAdminPageUris() {
        return pageUris.stream().filter(e -> e.startsWith(basePath + "admin/")).map(e -> e.split("\\?")[0]).collect(Collectors.toSet());
    }

    @Override
    public Set<String> getAdminStaticCacheUris() {
        Set<String> cacheUris = new HashSet<>(getAdminPageUris());
        cacheUris.add(basePath + AdminConstants.ADMIN_PWA_MANIFEST_JSON.substring(1));
        cacheUris.add(basePath + AdminConstants.ADMIN_SERVICE_WORKER_JS.substring(1));
        return cacheUris;
    }

    @Override
    public Set<String> getAdminCacheableApiUris() {
        return apiUris;
    }

    @Override
    public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
        String lang = I18nUtil.getCurrentLocale();
        AdminResourceInfoResponse response = new AdminResourceInfoResponse();
        PublicWebSiteInfo publicWebSiteInfo = AdminConstants.getPublicWebSiteInfo();
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
        FeatureLabWebSiteInfo featureLab = new WebSiteService().featureLab();
        response.setFeature_webhook_enabled(featureLab.getFeature_webhook_enabled());
        response.setFeature_personal_data_enabled(featureLab.getFeature_personal_data_enabled());
        PasskeyRequestContext.Context passkeyContext = resolvePasskeyContext(request);
        response.setPasskeyLoginEnabled(isPasskeyLoginEnabled(passkeyContext));
        return response;
    }

    private PasskeyRequestContext.Context resolvePasskeyContext(HttpRequest request) {
        try {
            return new PasskeyRequestContext().resolvePage(request);
        } catch (PasskeyVerificationException e) {
            return null;
        }
    }

    private boolean isPasskeyLoginEnabled(PasskeyRequestContext.Context context) {
        if (context == null) {
            return false;
        }
        try {
            return new UserPasskey().hasAny(context.getOrigin(), context.getRpId());
        } catch (SQLException e) {
            LOGGER.warning("Query Passkey login status error, " + e.getMessage());
            return false;
        }
    }

}
