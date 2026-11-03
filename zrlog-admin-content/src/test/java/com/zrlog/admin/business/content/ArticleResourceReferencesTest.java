package com.zrlog.admin.business.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zrlog.model.Log;
import org.junit.Test;

public class ArticleResourceReferencesTest {
    @Test
    public void shouldReplaceContextPathLocalResourceReference() {
        ArticleResourceReferences service = new ArticleResourceReferences("/blog");
        Log article = new Log();
        article.setMarkdown("![cover](/blog/attached/image/a.png?v=1)");
        ArticleResourceReferences.Changes updates =
                service.replace(article, "/attached/image/a.png", "/attached/image/b.png", false);
        assertEquals("![cover](/blog/attached/image/b.png?v=1)", updates.getMarkdown());
    }

    @Test
    public void shouldReplaceLocalResourceReferencesByPrefixAcrossArticleFields() {
        ArticleResourceReferences service = new ArticleResourceReferences("/blog");
        Log article = new Log();
        article.setThumbnail("/blog/attached/images/a.png?x=1");
        article.setContent(
                "<img src=\"/blog/attached/images/a.png\"><a download href=\"/blog/attached/images/doc.pdf\">doc</a>");
        article.setMarkdown("![cover](/blog/attached/images/b.png)");
        article.setDigest("<img src=\"/blog/attached/images/c.png\">");
        ArticleResourceReferences.Changes updates =
                service.replace(article, "/attached/images", "/attached/media", true);
        assertEquals("/blog/attached/media/a.png?x=1", updates.getThumbnail());
        assertEquals(
                "<img src=\"/blog/attached/media/a.png\"><a download href=\"/blog/attached/media/doc.pdf\">doc</a>",
                updates.getContent());
        assertEquals("![cover](/blog/attached/media/b.png)", updates.getMarkdown());
        assertEquals("<img src=\"/blog/attached/media/c.png\">", updates.getDigest());
    }

    @Test
    public void shouldReplaceExternalResourceReferencesByPrefixAcrossArticleFields() {
        ArticleResourceReferences service = new ArticleResourceReferences("");
        Log article = new Log();
        article.setThumbnail("https://cdn.example.com/assets/a.png?x=1#top");
        article.setContent(
                "<img src=\"https://cdn.example.com/assets/a.png\"><img src=\"https://cdn.example.com/other/a.png\">");
        article.setMarkdown("![cover](https://cdn.example.com/assets/b.png)");
        article.setDigest("<img src=\"https://cdn.example.com/assets/c.png\">");
        ArticleResourceReferences.Changes updates =
                service.replace(
                        article,
                        "https://cdn.example.com/assets",
                        "https://static.example.net/media",
                        true);
        assertEquals("https://static.example.net/media/a.png?x=1#top", updates.getThumbnail());
        assertEquals(
                "<img src=\"https://static.example.net/media/a.png\"><img src=\"https://cdn.example.com/other/a.png\">",
                updates.getContent());
        assertEquals("![cover](https://static.example.net/media/b.png)", updates.getMarkdown());
        assertEquals("<img src=\"https://static.example.net/media/c.png\">", updates.getDigest());
    }

    @Test
    public void shouldSkipResourceReplacementWhenMatchKeyIsInvalidOrUnchanged() {
        ArticleResourceReferences service = new ArticleResourceReferences("");
        Log article = new Log();
        article.setThumbnail("/attached/images/a.png");
        article.setContent("<img src=\"/attached/images/a.png\">");
        assertTrue(service.replace(article, "", "/attached/b.png", false).isEmpty());
        assertTrue(
                service.replace(article, "/attached/missing.png", "/attached/b.png", false)
                        .isEmpty());
    }
}
