package com.zrlog.admin.business.content;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class ArticleResourceReferencesTest {
    @Test
    public void shouldReplaceContextPathLocalResourceReference() {
        ArticleResourceReferences service = new ArticleResourceReferences("/blog");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("markdown", "![cover](/blog/attached/image/a.png?v=1)");
        Map<String, Object> updates =
                service.replace(article, "/attached/image/a.png", "/attached/image/b.png", false);
        assertEquals("![cover](/blog/attached/image/b.png?v=1)", updates.get("markdown"));
    }

    @Test
    public void shouldReplaceLocalResourceReferencesByPrefixAcrossArticleFields() {
        ArticleResourceReferences service = new ArticleResourceReferences("/blog");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "/blog/attached/images/a.png?x=1");
        article.put(
                "content",
                "<img src=\"/blog/attached/images/a.png\"><a download href=\"/blog/attached/images/doc.pdf\">doc</a>");
        article.put("markdown", "![cover](/blog/attached/images/b.png)");
        article.put("digest", "<img src=\"/blog/attached/images/c.png\">");
        Map<String, Object> updates =
                service.replace(article, "/attached/images", "/attached/media", true);
        assertEquals("/blog/attached/media/a.png?x=1", updates.get("thumbnail"));
        assertEquals(
                "<img src=\"/blog/attached/media/a.png\"><a download href=\"/blog/attached/media/doc.pdf\">doc</a>",
                updates.get("content"));
        assertEquals("![cover](/blog/attached/media/b.png)", updates.get("markdown"));
        assertEquals("<img src=\"/blog/attached/media/c.png\">", updates.get("digest"));
    }

    @Test
    public void shouldReplaceExternalResourceReferencesByPrefixAcrossArticleFields() {
        ArticleResourceReferences service = new ArticleResourceReferences("");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "https://cdn.example.com/assets/a.png?x=1#top");
        article.put(
                "content",
                "<img src=\"https://cdn.example.com/assets/a.png\"><img src=\"https://cdn.example.com/other/a.png\">");
        article.put("markdown", "![cover](https://cdn.example.com/assets/b.png)");
        article.put("digest", "<img src=\"https://cdn.example.com/assets/c.png\">");
        Map<String, Object> updates =
                service.replace(
                        article,
                        "https://cdn.example.com/assets",
                        "https://static.example.net/media",
                        true);
        assertEquals("https://static.example.net/media/a.png?x=1#top", updates.get("thumbnail"));
        assertEquals(
                "<img src=\"https://static.example.net/media/a.png\"><img src=\"https://cdn.example.com/other/a.png\">",
                updates.get("content"));
        assertEquals("![cover](https://static.example.net/media/b.png)", updates.get("markdown"));
        assertEquals("<img src=\"https://static.example.net/media/c.png\">", updates.get("digest"));
    }

    @Test
    public void shouldSkipResourceReplacementWhenMatchKeyIsInvalidOrUnchanged() {
        ArticleResourceReferences service = new ArticleResourceReferences("");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("thumbnail", "/attached/images/a.png");
        article.put("content", "<img src=\"/attached/images/a.png\">");
        assertTrue(service.replace(article, "", "/attached/b.png", false).isEmpty());
        assertTrue(
                service.replace(article, "/attached/missing.png", "/attached/b.png", false)
                        .isEmpty());
    }
}
