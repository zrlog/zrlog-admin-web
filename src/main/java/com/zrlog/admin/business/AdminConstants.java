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
    public static final String ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH = ADMIN_URI_BASE_PATH + "/dev/file/tmp";
    public static final String ADMIN_DB_ATTACHED_TMP = AdminConstants.ADMIN_URI_BASE_PATH + "/attached/tmp";

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
        TITLE_MAP.put(ADMIN_LOGIN_URI_PATH, "admin.login.title");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article-edit", "admin.article.edit.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article", "admin.article.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/index", "admin.dashboard.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/comment", "admin.comment.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website", "admin.website.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/blog", "admin.website.blog.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/admin", "admin.website.admin.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/template", "admin.website.template.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/other", "admin.website.other.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/upgrade", "admin.website.upgrade.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/ai", "admin.website.ai.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/article-edit", "admin.website.articleEdit.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/content-protector", "admin.website.contentProtector.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/webhook", "admin.website.webhook.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/privacy", "admin.website.privacy.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/website/lab", "admin.website.lab.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/nav", "admin.nav.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/tag", "admin.tag.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/article-type", "admin.articleType.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/link", "admin.link.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/plugin", "admin.plugin.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/user", "admin.user.info.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/user-update-password", "admin.user.password.change.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/upgrade", "admin.upgrade.wizard.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/template-config", "admin.template.config.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/template-center", "admin.template.center.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/system", "admin.system.info.manage");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/file-manager", "admin.fileManager.title");
        TITLE_MAP.put(ADMIN_URI_BASE_PATH + "/dev", "admin.dev.manage");
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
        sj.add(I18nUtil.getAdminBackendStringFromRes("admin.management.title"));
        return sj.toString();
    }

    public static long getAutoDigestLength() {
        return getPublicWebSiteInfo().getArticle_auto_digest_length();
    }
}
