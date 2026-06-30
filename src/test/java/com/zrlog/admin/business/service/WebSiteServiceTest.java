package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.AIWebSiteInfoWithAIMessages;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.request.AddArticleAIContextRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.data.util.WebSiteUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebSiteServiceTest {

    @Test
    public void shouldNormalizeArticleEditWebsiteInfo() throws Exception {
        WebSiteService service = new WebSiteService();
        ArticleEditWebSiteInfo invalid = new ArticleEditWebSiteInfo();
        invalid.setArticle_auto_digest_length(0L);
        invalid.setArticle_edit_auto_save_interval(99L);
        invalid.setArticle_editor_link_preview_enabled(null);
        invalid.setArticle_publish_check_enabled(null);
        invalid.setArticle_cover_aspect_ratio("bad");
        ArticleEditWebSiteInfo explicit = new ArticleEditWebSiteInfo();
        explicit.setArticle_auto_digest_length(120L);
        explicit.setArticle_edit_auto_save_interval(10L);
        explicit.setArticle_editor_link_preview_enabled(true);
        explicit.setArticle_publish_check_enabled(false);
        explicit.setArticle_cover_aspect_ratio("1:1");

        ArticleEditWebSiteInfo normalizedInvalid = service.normalizeArticleEditWebSiteInfo(invalid);
        ArticleEditWebSiteInfo normalizedExplicit = service.normalizeArticleEditWebSiteInfo(explicit);

        assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH),
                normalizedInvalid.getArticle_auto_digest_length());
        assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_EDIT_AUTO_SAVE_INTERVAL,
                normalizedInvalid.getArticle_edit_auto_save_interval());
        assertEquals(false, normalizedInvalid.getArticle_editor_link_preview_enabled());
        assertEquals(true, normalizedInvalid.getArticle_publish_check_enabled());
        assertEquals(ArticleEditWebSiteInfo.DEFAULT_ARTICLE_COVER_ASPECT_RATIO,
                normalizedInvalid.getArticle_cover_aspect_ratio());
        assertEquals(Long.valueOf(120L), normalizedExplicit.getArticle_auto_digest_length());
        assertEquals(Long.valueOf(10L), normalizedExplicit.getArticle_edit_auto_save_interval());
        assertEquals(true, normalizedExplicit.getArticle_editor_link_preview_enabled());
        assertEquals(false, normalizedExplicit.getArticle_publish_check_enabled());
        assertEquals("1:1", normalizedExplicit.getArticle_cover_aspect_ratio());
    }

    @Test
    public void shouldFillAiMessagesFromSerializedCacheValue() throws Exception {
        WebSiteService service = new WebSiteService();
        AIWebSiteInfoWithAIMessages info = new AIWebSiteInfoWithAIMessages();

        service.fillAiMessages(info, Map.of("ai_chat_message_7",
                "[{\"role\":\"user\",\"content\":\"hello\",\"messageId\":\"m1\"}]"), "ai_chat_message_7");

        assertEquals(1, info.getAiMessages().size());
        assertEquals("user", info.getAiMessages().get(0).getRole());
        assertEquals("hello", info.getAiMessages().get(0).getContent());
        assertEquals("m1", info.getAiMessages().get(0).getMessageId());

        service.fillAiMessages(info, Map.of(), "missing");
        assertEquals(List.of(), info.getAiMessages());
    }

    @Test
    public void shouldEnsureSystemMessageOnlyWhenMissing() {
        WebSiteService service = new WebSiteService();
        List<AIResponseEntry.AIContentEntry> messages = new ArrayList<>();
        messages.add(new AIResponseEntry.AIContentEntry("user", "question"));

        service.ensureSystemMessage(messages, "system prompt");
        service.ensureSystemMessage(messages, "ignored");

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("system prompt", messages.get(0).getContent());
        assertEquals("user", messages.get(1).getRole());
    }

    @Test
    public void shouldBuildArticleContextContentAndMeta() throws Exception {
        WebSiteService service = new WebSiteService();
        AddArticleAIContextRequest request = new AddArticleAIContextRequest();
        request.setTitle("Title");
        request.setDigest("Digest");
        request.setKeywords("java,zrlog");
        request.setMarkdown("Markdown");
        request.setArticleVersion(3);

        String content = service.buildArticleContextContent(request);
        AIResponseEntry.AIContentEntry.ArticleContextMeta meta =
                service.buildArticleContextMeta(request);

        assertTrue(content.startsWith("Article context snapshot."));
        assertTrue(content.contains("Article version: 3"));
        assertTrue(content.contains("Title: Title"));
        assertTrue(content.contains("Digest: Digest"));
        assertTrue(content.contains("Keywords: java,zrlog"));
        assertTrue(content.endsWith("Markdown:\nMarkdown"));
        assertEquals("Title", meta.getTitle());
        assertEquals(Integer.valueOf(3), meta.getArticleVersion());
        assertEquals(Integer.valueOf(8), meta.getMarkdownLength());
        assertNotNull(meta.getCreatedAt());
    }

    @Test
    public void shouldFillMissingAiMessageIdsAndBuildCacheKeys() throws Exception {
        WebSiteService service = new WebSiteService();
        AIResponseEntry.AIContentEntry existing = new AIResponseEntry.AIContentEntry("user", "existing");
        existing.setMessageId("existing-id");
        AIResponseEntry.AIContentEntry missing = new AIResponseEntry.AIContentEntry("assistant", "missing");
        List<AIResponseEntry.AIContentEntry> messages = List.of(existing, missing);

        service.fillMissingMessageIds(messages);

        assertEquals("existing-id", existing.getMessageId());
        assertNotNull(missing.getMessageId());
        assertFalse(missing.getMessageId().isEmpty());
        assertEquals("ai_chat_message_42", WebSiteService.buildCacheKey(42L));
        assertEquals("", service.emptyToBlank(null));
        assertEquals("value", service.emptyToBlank("value"));
    }
}
