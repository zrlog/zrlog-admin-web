package com.zrlog.admin.business.service;

import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.service.MarkdownRenderer;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArticlePublishingRuntimeTest {
    @Test
    public void standaloneAdminCanPrepareArticlesWithoutBlogEngines() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.zrlog.blog.polyglot.markdown.MarkdownJsRenderer"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.graalvm.polyglot.Context"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.zrlog.blog.freemarker.template.FreemarkerZrLogTemplate"));

        try (var db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            ServerConfig config = db.config().getServerConfig();
            MarkdownRenderer renderer = config.getBindings().build().get(MarkdownRenderer.class);
            ArticlePublishingService service =
                    new ArticlePublishingService(
                            null,
                            null,
                            null,
                            renderer,
                            ApplicationServicesFixture.audit(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()));
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
}
