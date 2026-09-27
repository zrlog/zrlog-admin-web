package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.base.BasicWebSiteInfo;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.util.WebSiteUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebSiteServiceDatabaseTest {

    @Test
    public void shouldReadWebsiteGroupsFromInstallSchemaBackedTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("title", "Demo Blog");
            db.putWebsite("second_title", "Second");
            db.putWebsite("description", "Description");
            db.putWebsite("keywords", "zrlog,java");
            db.putWebsite("author", "Author");
            db.putWebsite("generator_html_status", true);
            db.putWebsite("disable_comment_status", false);
            db.putWebsite("article_thumbnail_status", true);
            db.putWebsite("system_notification", "notice");

            WebSiteService service = new WebSiteService();
            BasicWebSiteInfo basic = service.basicWebSiteInfo();
            BlogWebSiteInfo blog = service.blogWebSiteInfo();

            assertEquals("Demo Blog", basic.getTitle());
            assertEquals("Second", basic.getSecond_title());
            assertEquals("Description", basic.getDescription());
            assertEquals("zrlog,java", basic.getKeywords());
            assertEquals("Author", basic.getAuthor());
            assertEquals(Boolean.TRUE, blog.getGenerator_html_status());
            assertEquals(Boolean.FALSE, blog.getDisable_comment_status());
            assertEquals(Boolean.TRUE, blog.getArticle_thumbnail_status());
            assertEquals("notice", blog.getSystem_notification());
        }
    }

    @Test
    public void shouldNormalizeAdminAndArticleEditorSettingsFromWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("admin_color_primary", "");
            db.putWebsite("session_timeout", -1);
            db.putWebsite("admin_article_page_size", 0);
            db.putWebsite("article_auto_digest_length", 0);
            db.putWebsite(WebSiteService.ARTICLE_EDIT_AUTO_SAVE_INTERVAL_KEY, 99);
            db.putWebsite(WebSiteService.ARTICLE_EDITOR_LINK_PREVIEW_ENABLED_KEY, false);
            db.putWebsite(WebSiteService.ARTICLE_PUBLISH_CHECK_ENABLED_KEY, false);
            db.putWebsite(WebSiteService.ARTICLE_COVER_ASPECT_RATIO_KEY, "bad");

            WebSiteService service = new WebSiteService();
            AdminWebSiteInfo admin = service.adminWebSiteInfo();
            ArticleEditWebSiteInfo articleEdit = service.articleEditWebSiteInfo();

            assertEquals(WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR, admin.getAdmin_color_primary());
            assertEquals(Long.valueOf(10L), admin.getAdmin_article_page_size());
            assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_SESSION_TIMEOUT / 60 / 1000), admin.getSession_timeout());
            assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH), articleEdit.getArticle_auto_digest_length());
            assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_EDIT_AUTO_SAVE_INTERVAL, articleEdit.getArticle_edit_auto_save_interval());
            assertEquals(Boolean.FALSE, articleEdit.getArticle_editor_link_preview_enabled());
            assertEquals(Boolean.FALSE, articleEdit.getArticle_publish_check_enabled());
            assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_COVER_ASPECT_RATIO, articleEdit.getArticle_cover_aspect_ratio());
        }
    }

    @Test
    public void shouldReadFeatureLabFlags() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(WebSiteService.FEATURE_RESOURCE_REFERENCE_ENABLED_KEY, true);
            db.putWebsite(WebSiteService.FEATURE_ARTICLE_EXTENSION_FILTER_ENABLED_KEY, true);
            db.putWebsite(WebSiteService.FEATURE_WEBHOOK_ENABLED_KEY, false);
            db.putWebsite(WebSiteService.FEATURE_PERSONAL_DATA_ENABLED_KEY, true);
            FeatureLabWebSiteInfo featureLab = new FeatureLabService().featureLab();

            assertEquals(Boolean.TRUE, featureLab.getFeature_resource_reference_enabled());
            assertEquals(Boolean.TRUE, featureLab.getFeature_article_extension_filter_enabled());
            assertEquals(Boolean.FALSE, featureLab.getFeature_webhook_enabled());
            assertEquals(Boolean.TRUE, featureLab.getFeature_personal_data_enabled());
            assertTrue(new WebSiteService().isFeatureResourceReferenceEnabled());
            assertTrue(new WebSiteService().isFeatureArticleExtensionFilterEnabled());
        }
    }
}
