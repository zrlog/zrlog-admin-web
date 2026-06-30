package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.base.AdminWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.base.BasicWebSiteInfo;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.base.FeatureLabWebSiteInfo;
import com.zrlog.admin.business.rest.request.AddArticleAIContextRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.util.WebSiteUtils;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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
    public void shouldPersistAiMessagesThroughWebsiteKvTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-test");
            db.putWebsite("ai_api_key", "key");
            db.putWebsite("ai_prompt", "system");
            WebSiteService service = new WebSiteService();
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "hello"));

            assertTrue(service.saveAIMessage(messages, 7L));
            AIWebSiteInfoWithAIMessages info = service.getAiMessageInfoByArticleId(7L);

            assertEquals("OPEN_AI", info.getAi_provider().name());
            assertEquals("gpt-test", info.getAi_model());
            assertEquals(1, info.getAiMessages().size());
            assertEquals("user", info.getAiMessages().get(0).getRole());
            assertFalse(info.getAiMessages().get(0).getMessageId().isEmpty());
            assertEquals(1L, ((Number) db.scalar("select count(1) from website where name=?", "ai_chat_message_7")).longValue());
            assertTrue(service.clearAIMessage(7L));
            assertEquals(null, db.queryOne("select value from website where name=?", "ai_chat_message_7").get("value"));
        }
    }

    @Test
    public void shouldAppendArticleContextToPersistedAiMessages() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("ai_prompt", "system prompt");
            WebSiteService service = new WebSiteService();
            AddArticleAIContextRequest context = new AddArticleAIContextRequest();
            context.setTitle("Article");
            context.setMarkdown("Markdown");
            context.setArticleVersion(2);

            List<AIResponseEntry.AIContentEntry> messages = service.appendArticleContextMessage(9L, context);
            Map<String, Object> row = db.queryOne("select value from website where name=?", "ai_chat_message_9");

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertEquals("user", messages.get(1).getRole());
            assertEquals("articleContext", messages.get(1).getMessageType());
            assertTrue(String.valueOf(row.get("value")).contains("Article context snapshot."));
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
    public void shouldReadFeatureLabFlagsAndMigrateDraftAiMessages() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(WebSiteService.FEATURE_RESOURCE_REFERENCE_ENABLED_KEY, true);
            db.putWebsite(WebSiteService.FEATURE_WEBHOOK_ENABLED_KEY, false);
            db.putWebsite(WebSiteService.FEATURE_PERSONAL_DATA_ENABLED_KEY, true);
            WebSiteService service = new WebSiteService();
            List<AIResponseEntry.AIContentEntry> messages =
                    List.of(new AIResponseEntry.AIContentEntry("user", "draft"));
            assertTrue(service.saveAIMessage(messages, 0L));

            FeatureLabWebSiteInfo featureLab = service.featureLab();

            assertEquals(Boolean.TRUE, featureLab.getFeature_resource_reference_enabled());
            assertEquals(Boolean.FALSE, featureLab.getFeature_webhook_enabled());
            assertEquals(Boolean.TRUE, featureLab.getFeature_personal_data_enabled());
            assertTrue(service.isFeatureResourceReferenceEnabled());
            assertTrue(service.migrateDraftAIMessageToArticle(12L));
            assertEquals(1, service.getAiMessageInfoByArticleId(12L).getAiMessages().size());
            assertEquals(0, service.getAiMessageInfoByArticleId(0L).getAiMessages().size());
            assertFalse(service.migrateDraftAIMessageToArticle(0L));
            assertFalse(service.removeAIMessage(0L));
            assertFalse(service.clearAIMessage(-1L));
        }
    }
}
