package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.ZrLogConfig;

final class ContentToolsFixture {
    private ContentToolsFixture() { }

    static ContentToolFactory from(ZrLogConfig config) {
        var cache = new InMemoryZrLogDatabase.TestCacheService();
        var articles = new AdminArticleService(cache,
                ApplicationServicesFixture.capability(config, ArticleAssistant.class), new com.zrlog.admin.business.service.AdminPageDataFactory(com.zrlog.admin.support.ApplicationServicesFixture.site(config), () -> "test"));
        var publishing = new ArticlePublishingService(articles,
                ApplicationServicesFixture.cacheRefresh(cache, config), markdown -> null);
        return new ContentToolFactory(new ContentToolService(publishing),
                ApplicationServicesFixture.capability(config, AttachmentStorage.class), ApplicationServicesFixture.backend(config)::configured, () -> Boolean.TRUE.equals(cache.getPublicWebSiteInfo().getGenerator_html_status()));
    }
}
