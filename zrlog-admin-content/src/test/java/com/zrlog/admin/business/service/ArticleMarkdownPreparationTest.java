package com.zrlog.admin.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.support.ApplicationServicesFixture;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class ArticleMarkdownPreparationTest {
    @Test
    public void shouldUseInjectedRendererOnlyForMarkdownWithoutHtml() {
        AtomicInteger calls = new AtomicInteger();
        ArticlePublishingService service =
                new ArticlePublishingService(
                        null,
                        null,
                        markdown -> {
                            calls.incrementAndGet();
                            assertEquals("# Title", markdown);
                            return "<h1>Title</h1>";
                        },
                        ApplicationServicesFixture.audit(null),
                        ApplicationServicesFixture.operations(null));
        CreateArticleRequest request = new CreateArticleRequest();
        request.setEditorType("markdown");
        request.setMarkdown("# Title");

        service.prepareRequest(request);
        assertEquals("<h1>Title</h1>", request.getContent());
        service.prepareRequest(request);
        assertEquals(1, calls.get());

        request.setContent(null);
        request.setEditorType("html");
        service.prepareRequest(request);
        assertNull(request.getContent());
        assertEquals(1, calls.get());

        request.setEditorType("markdown");
        request.setMarkdown("");
        service.prepareRequest(request);
        assertEquals(1, calls.get());
    }

    @Test
    public void unavailableRendererShouldPreserveExistingContent() {
        ArticlePublishingService service =
                new ArticlePublishingService(
                        null,
                        null,
                        markdown -> null,
                        ApplicationServicesFixture.audit(null),
                        ApplicationServicesFixture.operations(null));
        CreateArticleRequest request = new CreateArticleRequest();
        request.setEditorType("markdown");
        request.setMarkdown("# Title");
        request.setContent("");
        service.prepareRequest(request);
        assertEquals("", request.getContent());
    }
}
