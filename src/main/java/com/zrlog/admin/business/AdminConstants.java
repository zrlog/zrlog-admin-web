package com.zrlog.admin.business;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.util.I18nUtil;

import java.util.*;

public class AdminConstants {
    public static final String ADMIN_URI_BASE_PATH = "/admin";
    public static final String ADMIN_DEV_URI_BASE_PATH = ADMIN_URI_BASE_PATH + "/dev";
    public static final String ADMIN_DEV_FILE_URI_BASE_PATH = ADMIN_URI_BASE_PATH + "/dev/file";

    public static final String ADMIN_HTML_PAGE = ADMIN_URI_BASE_PATH + "/index.html";
    public static final String ADMIN_LOGIN_URI_PATH = ADMIN_URI_BASE_PATH + "/login";
    public static final String ADMIN_PWA_MANIFEST_API_URI_PATH = "/api" + ADMIN_URI_BASE_PATH + "/manifest";
    public static final String ADMIN_REFRESH_CACHE_API_URI_PATH = "/api" + ADMIN_URI_BASE_PATH + "/refreshCache";
    public static final String ADMIN_TITLE_CHAR = " - ";
    public static final String AUTO_UPGRADE_VERSION_KEY = "autoUpgradeVersion";
    public static final Map<String, String> TITLE_MAP = new TreeMap<>();
    public static final String INDEX_URI_PATH = "/index";
    public static final String ADMIN_PWA_MANIFEST_JSON = ADMIN_URI_BASE_PATH + "/manifest.json";
    public static final String ADMIN_SERVICE_WORKER_JS = ADMIN_URI_BASE_PATH + "/service-worker.js";

    public static final String FAVICON_ICO_URI_PATH = "/favicon.ico";
    public static final String FAVICON_PNG_PWA_192_URI_PATH = ADMIN_URI_BASE_PATH + "/pwa/icon/favicon-192.png";
    public static final String FAVICON_PNG_PWA_512_URI_PATH = ADMIN_URI_BASE_PATH + "/pwa/icon/favicon-512.png";
    public static final String ATTACHED_FOLDER = "/attached/";
    public static final String BUILD_SYSTEM_INFO_MD = "/build_system_info.md";

    public static final List<String> adminStaticResources = Arrays.asList(ADMIN_URI_BASE_PATH + "/static", ADMIN_URI_BASE_PATH + "/pwa");

    static {
        TITLE_MAP.put(ADMIN_LOGIN_URI_PATH, "login");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article-edit", "admin.log.edit");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article", "blogManage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/index", "dashboard");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/comment", "comment");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website", "admin.website.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/blog", "admin.blog.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/admin", "admin.admin.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/template", "admin.template.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/other", "admin.other.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/upgrade", "admin.upgrade.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/ai", "admin.ai.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/nav", "admin.nav.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article-type", "admin.type.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/link", "admin.link.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/plugin", "admin.plugin.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/user", "admin.user.info");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/user-update-password", "admin.changePwd");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/upgrade", "upgradeWizard");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/template-config", "templateConfig");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/template-center", "templateCenter");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/system", "systemInfo");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/dev", "dev");
    }

    public static AdminResource adminResource;


    public static String getAdminDocumentTitleByUri(String uri) {
        String realUri = uri.replaceFirst("/api", "");
        String key = TITLE_MAP.get(realUri);
        if (Objects.isNull(key)) {
            return getAdminTitle("");
        }
        return getAdminTitle(I18nUtil.getAdminBackendStringFromRes(key));
    }

    public static String getAdminDocumentTitleByUri(String uri, PublicWebSiteInfo publicWebSiteInfo) {
        String realUri = uri.replaceFirst("/api", "");
        String key = TITLE_MAP.get(realUri);
        if (Objects.isNull(key)) {
            return getAdminTitle("");
        }
        return getAdminTitle(I18nUtil.getAdminBackendStringFromRes(key), publicWebSiteInfo);
    }

    public static PublicWebSiteInfo getPublicWebSiteInfo() {
        if (Objects.isNull(Constants.zrLogConfig)) {
            return WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo());
        }

        CacheService cacheService = Constants.zrLogConfig.getCacheService();
        if (Objects.isNull(cacheService)) {
            return WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo());
        }
        return cacheService.getPublicWebSiteInfo();
    }

    public static String getAdminTitle(String startTitle) {
        return getAdminTitle(startTitle, getPublicWebSiteInfo());
    }

    public static String getAdminTitle(String startTitle, PublicWebSiteInfo publicWebSiteInfo) {
        String title = publicWebSiteInfo.getTitle();
        StringJoiner sj = new StringJoiner(ADMIN_TITLE_CHAR);
        if (StringUtils.isNotEmpty(startTitle) && !startTitle.trim().isEmpty()) {
            sj.add(startTitle);
        }
        if (StringUtils.isNotEmpty(title)) {
            sj.add(title);
        }
        sj.add(I18nUtil.getAdminBackendStringFromRes("admin.management"));
        return sj.toString();
    }

    public static long getAutoDigestLength() {
        return getPublicWebSiteInfo().getArticle_auto_digest_length();
    }
}
