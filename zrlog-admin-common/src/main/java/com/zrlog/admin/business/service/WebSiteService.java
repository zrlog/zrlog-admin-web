package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.WebSite;

import java.util.*;

public class WebSiteService {

    public static final String ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY = "article_editor_link_preview_enabled";
    public static final String ARTICLE_PUBLISH_CHECK_ENABLED_KEY = "article_publish_check_enabled";
    public static final String ARTICLE_COVER_ASPECT_RATIO_KEY = "article_cover_aspect_ratio";
    public static final String ARTICLE_EDIT_AUTO_SAVE_INTERVAL_KEY = "article_edit_auto_save_interval";
    public static final String FEATURE_RESOURCE_REFERENCE_ENABLED_KEY = "feature_resource_reference_enabled";
    public static final String FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY =
            "feature_article_extension_filter_enabled";
    public static final String FEATURE_WEBHOOK_ENABLED_KEY = "feature_webhook_enabled";
    public static final String FEATURE_PERSONAL_DATA_ENABLED_KEY = "feature_personal_data_enabled";
    private static final List<String> ARTICLE_EDIT_WEBSITE_INFO_KEYS = Arrays.asList(WebSite.article_auto_digest_length,
            ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY, ARTICLE_PUBLISH_CHECK_ENABLED_KEY,
            ARTICLE_COVER_ASPECT_RATIO_KEY, ARTICLE_EDIT_AUTO_SAVE_INTERVAL_KEY);
    public UpgradeWebSiteInfo upgradeWebSiteInfo() {
        return new WebsiteKvService().upgradeWebSiteInfo();
    }

    public boolean isFeatureResourceReferenceEnabled() {
        return Objects.equals(true, queryToMap(List.of(FEATURE_RESOURCE_REFERENCE_ENABLED_KEY), FeatureLabWebSiteInfo.class).getFeature_resource_reference_enabled());
    }

    public boolean isFeatureArticleExtensionFilterEnabled() {
        return Objects.equals(true, queryToMap(List.of(FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY), FeatureLabWebSiteInfo.class).getFeature_article_extension_filter_enabled());
    }

    public BlogWebSiteInfo blogWebSiteInfo() {
        BlogWebSiteInfo blog = queryToMap(Arrays.asList(WebSite.generator_html_status, WebSite.host, WebSite.disable_comment_status, WebSite.article_thumbnail_status, WebSite.system_notification), BlogWebSiteInfo.class);
        blog.setGenerator_html_status(Objects.equals(blog.getGenerator_html_status(), true));
        blog.setDisable_comment_status(Objects.equals(blog.getDisable_comment_status(), true));
        blog.setArticle_thumbnail_status(Objects.equals(blog.getArticle_thumbnail_status(), true));
        return blog;
    }

    public BasicWebSiteInfo basicWebSiteInfo() {
        return queryToMap(Arrays.asList(WebSite.title, WebSite.second_title, WebSite.description, WebSite.keywords, "favicon_ico_base64", WebSite.author), BasicWebSiteInfo.class);
    }

    public AdminWebSiteInfo adminWebSiteInfo() {
        AdminWebSiteInfo admin = queryToMap(Arrays.asList(WebSite.admin_darkMode, WebSite.admin_compactMode,
                WebSite.language, WebSite.admin_color_primary, WebSite.admin_theme,
                WebSite.session_timeout, "favicon_png_pwa_512_base64",
                "favicon_png_pwa_192_base64", "admin_static_resource_base_url", com.zrlog.admin.util.BackendServerUrl.SETTING_KEY),
                AdminWebSiteInfo.class);
        admin.setBackend_server_url(Objects.toString(admin.getBackend_server_url(), ""));
        if (StringUtils.isEmpty(admin.getAdmin_color_primary())) {
            admin.setAdmin_color_primary(WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR);
        }
        String pageSize = new WebSite().getStringValueByName("admin_article_page_size");
        long parsedSize = 10;
        try {
            if (StringUtils.isNotEmpty(pageSize)) parsedSize = Long.parseLong(pageSize);
        } catch (NumberFormatException ignored) { /* Invalid legacy values use the default. */ }
        admin.setAdmin_article_page_size(parsedSize > 0 ? parsedSize : 10L);
        if (Objects.isNull(admin.getSession_timeout()) || admin.getSession_timeout() <= 0) {
            admin.setSession_timeout(WebSiteUtils.DEFAULT_SESSION_TIMEOUT / 60 / 1000);
        }
        return admin;
    }

    public ArticleEditWebSiteInfo articleEditWebSiteInfo() {
        ArticleEditWebSiteInfo articleEdit = queryToMap(ARTICLE_EDIT_WEBSITE_INFO_KEYS, ArticleEditWebSiteInfo.class);
        return normalizeArticleEditWebSiteInfo(articleEdit);
    }

    static ArticleEditWebSiteInfo normalizeArticleEditWebSiteInfo(ArticleEditWebSiteInfo articleEdit) {
        if (Objects.isNull(articleEdit.getArticle_auto_digest_length()) || articleEdit.getArticle_auto_digest_length() <= 0) {
            articleEdit.setArticle_auto_digest_length(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH);
        }
        articleEdit.setArticle_edit_auto_save_interval(ArticleEditWebSiteInfo.normalizeArticleEditAutoSaveInterval(articleEdit.getArticle_edit_auto_save_interval()));
        articleEdit.setArticle_editor_link_preview_enabled(Objects.equals(articleEdit.getArticle_editor_link_preview_enabled(), true));
        articleEdit.setArticle_publish_check_enabled(!Objects.equals(articleEdit.getArticle_publish_check_enabled(), false));
        articleEdit.setArticle_cover_aspect_ratio(ArticleEditWebSiteInfo.normalizeArticleCoverAspectRatio(articleEdit.getArticle_cover_aspect_ratio()));
        return articleEdit;
    }

    public ContentProtectorWebSiteInfo contentProtector() {
        ContentProtectorWebSiteInfo contentProtector = queryToMap(Arrays.asList(WebSite.content_protector_enabled,
                WebSite.content_protector_license_type, WebSite.content_protector_template),
                ContentProtectorWebSiteInfo.class);
        contentProtector.doValid();
        return contentProtector;
    }

    public boolean isAdminLinkPreviewEnabled() {
        return Objects.equals(Boolean.TRUE, queryToMap(Arrays.asList(ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY),
                ArticleEditWebSiteInfo.class).getArticle_editor_link_preview_enabled());
    }

    private <T> T queryToMap(List<String> names, Class<T> clazz) {
        Map<String, Object> webSiteByNameIn = new WebSite().getWebSiteByNameIn(names);
        return ResultBeanUtils.convert(webSiteByNameIn, clazz);
    }

    public OtherWebSiteInfo other() {
        return queryToMap(Arrays.asList(WebSite.icp, WebSite.webCm, WebSite.robotRuleContent), OtherWebSiteInfo.class);
    }
}
