package com.zrlog.admin.business.ai.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.knowledge.ContentToolProvider;
import com.zrlog.admin.business.knowledge.KnowledgeService;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.admin.data.repository.LogVersionRepository;
import com.zrlog.data.repository.TagRepository;
import com.zrlog.data.repository.TypeRepository;

final class ContentToolsFixture {
    private ContentToolsFixture() {
    }

    static ContentToolFactory from(ZrLogConfig config) {
        var cache = new InMemoryZrLogDatabase.TestCacheService();
        var articles =
                new AdminArticleService(ApplicationServicesFixture.capability(config, ArticleAssistant.class),
                        AccountServicesFixture.component(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper) config.getDataSource())
                                .permissions(),
                        AccountServicesFixture.component(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper) config.getDataSource())
                                .identities(),
                        AccountServicesFixture.component(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper) config.getDataSource())
                                .preferences(),
                        new ArticleVersionService(AccountServicesFixture.component(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper)
                                                config.getDataSource())
                                .permissions(),
                                () ->
                                        new LogRepository(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource()),
                                () ->
                                        new LogVersionRepository(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource())),
                        new ArticlePinningService(
                                () ->
                                        new LogRepository(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource()),
                                ApplicationServicesFixture.locks(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper) config.getDataSource())),
                        () ->
                                new LogRepository(
                                        config == null
                                                ? null
                                                : (DataSourceWrapper) config.getDataSource()),
                        ApplicationServicesFixture.locks(
                                config == null
                                        ? null
                                        : (DataSourceWrapper) config.getDataSource()));
        var publishing =
                new ArticlePublishingService(
                        articles,
                        new ArticleQueryService(cache,
                                ApplicationServicesFixture.capability(
                                        config, ArticleAssistant.class),
                                new AdminPageDataFactory(
                                        ApplicationServicesFixture.site(config),
                                        () -> "test",
                                        ApplicationServicesFixture.messages(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource())),
                                AccountServicesFixture.component(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource())
                                        .permissions(),
                                AccountServicesFixture.component(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource())
                                        .preferences(),
                                () ->
                                        new LogRepository(
                                                config == null
                                                        ? null
                                                        : (DataSourceWrapper)
                                                        config.getDataSource())),
                        ApplicationServicesFixture.cacheRefresh(cache, config),
                        markdown -> null,
                        ApplicationServicesFixture.audit(
                                config == null ? null : (DataSourceWrapper) config.getDataSource()),
                        ApplicationServicesFixture.operations(
                                config == null
                                        ? null
                                        : (DataSourceWrapper) config.getDataSource()));
        return new ContentToolFactory((toolAccount, toolScopes, toolRequest, toolLanguage, toolAudit, toolCredential, toolKnowledge) ->
                new ContentToolProvider(toolAccount,
                        toolAudit,
                        toolCredential,
                        toolScopes,
                        toolRequest,
                        toolLanguage,
                        new ContentToolService(publishing,
                                AccountServicesFixture.component(config == null ? null : (DataSourceWrapper) config.getDataSource()).permissions(),
                                () -> new TagRepository(config == null ? null : (DataSourceWrapper) config.getDataSource()),
                                () -> new TypeRepository(config == null ? null : (DataSourceWrapper) config.getDataSource())),
                        ApplicationServicesFixture.capability(
                                config, AttachmentStorage.class),
                        toolKnowledge,
                        AccountServicesFixture.component(config == null ? null : (DataSourceWrapper) config.getDataSource()).identities()),
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
                                () -> new LogRepository(config == null ? null : (DataSourceWrapper) config.getDataSource())),
                AccountServicesFixture.component(config == null ? null : (DataSourceWrapper) config.getDataSource()).identities());
    }
}
