package com.zrlog.admin.business.ai.service;

import com.hibegin.common.dao.DAO;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.knowledge.ContentToolProvider;
import com.zrlog.admin.business.knowledge.KnowledgeService;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.ArticlePinningService;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.business.service.ArticleVersionService;
import com.zrlog.admin.business.service.ContentToolService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.model.Log;
import com.zrlog.model.LogVersion;
import com.zrlog.model.Tag;

final class ContentToolsFixture {
    private ContentToolsFixture() { }

    static ContentToolFactory from(ZrLogConfig config) {
        var cache = new InMemoryZrLogDatabase.TestCacheService();
        var articles =
                new AdminArticleService(
                        cache,
                        ApplicationServicesFixture.capability(config, ArticleAssistant.class),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(config),
                                () -> "test",
                                ApplicationServicesFixture.messages(),
                                AccountServicesFixture.component().permissions()),
                        AccountServicesFixture.component().permissions(),
                        AccountServicesFixture.component().preferences(),
                        new ArticleVersionService(
                                AccountServicesFixture.component().permissions(),
                                Log::new,
                                () -> new LogVersion(DAO.getDefaultDataSource())),
                        new ArticlePinningService(Log::new, ApplicationServicesFixture.locks()),
                        Log::new,
                        ApplicationServicesFixture.locks());
        var publishing =
                new ArticlePublishingService(
                        articles,
                        ApplicationServicesFixture.cacheRefresh(cache, config),
                        markdown -> null,
                        ApplicationServicesFixture.audit(),
                        ApplicationServicesFixture.operations());
        return new ContentToolFactory(
                (toolScopes, toolRequest, toolLanguage, toolKnowledge) ->
                        new ContentToolProvider(
                                toolScopes,
                                toolRequest,
                                toolLanguage,
                                new ContentToolService(
                                        publishing,
                                        AccountServicesFixture.component().permissions(),
                                        Tag::new,
                                        com.zrlog.model.Type::new),
                                ApplicationServicesFixture.capability(
                                        config, AttachmentStorage.class),
                                toolKnowledge,
                                AccountServicesFixture.component().permissions()),
                (toolActor, toolScopes, toolLanguage) ->
                        new KnowledgeService(
                                toolActor,
                                toolScopes,
                                ApplicationServicesFixture.backend(config)::configured,
                                () ->
                                        Boolean.TRUE.equals(
                                                cache.getPublicWebSiteInfo()
                                                        .getGenerator_html_status()),
                                toolLanguage,
                                Log::new),
                AccountServicesFixture.component().permissions());
    }
}
