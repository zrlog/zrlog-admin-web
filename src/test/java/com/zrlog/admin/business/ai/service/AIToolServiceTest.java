package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AIToolServiceTest {

    @Test(expected = ArgsException.class)
    public void shouldRejectMarkdownRewriteWithoutDraftBody() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectMarkdownRewriteWhenDraftBodyIsTooShort() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setMarkdown(repeat("a", 119));

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test(expected = ArgsException.class)
    public void shouldTrimMarkdownBeforeRewriteLengthCheck() throws Exception {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setMarkdown("  " + repeat("a", 119) + "  ");

        new AIToolService().rewriteArticleMarkdown(request, "");
    }

    @Test
    public void shouldSummarizeMarkdownReferencesForPublishCheck() {
        String summary = new AIToolService().buildMarkdownReferenceSummary(
                "![cover](/assets/cover.png)\n[site](https://example.com)\nhttps://zrlog.com/path");

        assertTrue(summary.contains("imageReferenceCount: 1"));
        assertTrue(summary.contains("- /assets/cover.png"));
        assertTrue(summary.contains("externalLinkCount: 2"));
        assertTrue(summary.contains("- https://example.com"));
        assertTrue(summary.contains("- https://zrlog.com/path"));
    }

    @Test
    public void shouldBuildPublishContextForPublishCheck() {
        GenerateArticleFieldRequest request = new GenerateArticleFieldRequest();
        request.setAlias("release-check");
        request.setThumbnail("/attached/cover.png");
        request.setTransparentPublish(true);
        request.setStaticSiteEnabled(true);
        request.setStaticSitePluginEnabled(true);

        String summary = new AIToolService().buildPublishContextSummary(request);

        assertTrue(summary.contains("aliasStatus: present"));
        assertTrue(summary.contains("coverStatus: present"));
        assertTrue(summary.contains("staticSyncExpected: true"));
        assertTrue(summary.contains("structuredDataBoundary: theme-owned-public-output"));
        assertTrue(summary.contains("aiPublishCheckBlocksPublishing: false"));
    }

    private String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
