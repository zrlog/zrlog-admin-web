package com.zrlog.admin.business.service;

import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.web.ContentWebSetup;
import com.zrlog.business.service.MarkdownRenderer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ArticlePublishingRuntimeTest {
    @Test
    public void standaloneAdminCanPrepareArticlesWithoutBlogEngines() {
        ClassLoader loader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.graalvm.polyglot.Context"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.zrlog.blog.freemarker.template.FreemarkerZrLogTemplate"));

        ServerConfig config = new ServerConfig();
        new ContentWebSetup(config, () -> null, () -> null).setup();
        MarkdownRenderer renderer = config.getBindings().build().get(MarkdownRenderer.class);
        ArticlePublishingService service = new ArticlePublishingService(null, null, renderer);
        CreateArticleRequest body = new CreateArticleRequest();
        body.setEditorType("markdown");
        body.setMarkdown("# Title");
        service.prepareRequest(body);
        assertNull(body.getContent());
        body.setContent("<h1>Title</h1>");
        service.prepareRequest(body);
        assertEquals("<h1>Title</h1>", body.getContent());
    }
}
