package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebSiteServiceTest {

    @Test
    public void shouldNormalizeArticleEditWebsiteInfo() throws Exception {
        WebSiteService service = new WebSiteService();
        ArticleEditWebSiteInfo invalid = new ArticleEditWebSiteInfo();
        invalid.setArticle_auto_digest_length(-2L);
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
        for (long length : new long[]{-1, 0}) {
            ArticleEditWebSiteInfo special = new ArticleEditWebSiteInfo();
            special.setArticle_auto_digest_length(length);
            assertEquals(Long.valueOf(length), WebSiteService.normalizeArticleEditWebSiteInfo(special).getArticle_auto_digest_length());
        }
    }

}
