package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.AIArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.util.I18nUtil;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class AIPublishCheckServiceTest {
    @Test
    public void publishCheckUsesTheSameSiteSnapshotWithoutGlobalConfig() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            var service =
                    new AIPublishCheckService(
                            ApplicationServicesFixture.settings(db.dataSource()),
                            ApplicationServicesFixture.operations(db.dataSource()),
                            AIServiceDependencies.writing(db.dataSource()),
                            AIServiceDependencies.conversations(db.dataSource()),
                            com.zrlog.admin.support.AccountServicesFixture.component(db.dataSource()).identities(),
                            com.zrlog.admin.support.AccountServicesFixture.component(db.dataSource()).permissions());
            var context = new GenerateArticleFieldRequest();
            db.putWebsite("generator_html_status", "1");
            db.putWebsite("host", "blog.example");
            service.fillPublishCheckContext(context);
            assertTrue(context.getStaticSiteEnabled());
            assertTrue(context.getStaticSitePluginEnabled());
            db.putWebsite("host", "");
            service.fillPublishCheckContext(context);
            assertFalse(context.getStaticSitePluginEnabled());
            db.putWebsite("generator_html_status", "0");
            service.fillPublishCheckContext(context);
            assertFalse(context.getStaticSiteEnabled());
        }
    }

    @Test
    public void publishWorkerUsesTheRequestLanguageEvenWhenTheSiteAndLaterRequestDiffer() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.unitCacheService().getPublicWebSiteInfo().setLanguage("zh_CN");
            for (String language : List.of("en_US", "zh_CN")) {
                var started = new CountDownLatch(1);
                var proceed = new CountDownLatch(1);
                AIPublishCheckService service =
                        new AIPublishCheckService(
                                ApplicationServicesFixture.settings(db.dataSource()),
                                ApplicationServicesFixture.operations(db.dataSource()),
                                AIServiceDependencies.writing(db.dataSource()),
                                AIServiceDependencies.conversations(db.dataSource()),
                                com.zrlog.admin.support.AccountServicesFixture.component(db.dataSource()).identities(),
                                com.zrlog.admin.support.AccountServicesFixture.component(db.dataSource()).permissions()) {
                            @Override
                            PublishCheckResponse buildPublishCheckPayload(
                                    com.zrlog.data.security.AccountAccess actor, Long id,
                                    GenerateArticleFieldRequest context,
                                    PublishCheckPersistenceGuard guard,
                                    AIConversationService conversations) {
                                started.countDown();
                                try {
                                    assertTrue(proceed.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                return new PublishCheckResponse(
                                        null, I18nUtil.getCurrentLocale(), "test", List.of());
                            }
                        };
                AIArticleGlobalResponse detail = new AIArticleGlobalResponse();
                LoadEditArticleResponse article = new LoadEditArticleResponse();
                article.setLogId(1);
                detail.setArticle(article);
                detail.setAiConfigured(true);
                detail.setPublishCheckEnabled(true);
                AIPublishCheckService.PublishCheckTask task;
                try (var ignored = AdminLanguageContext.open(language)) {
                    task = service.startPublishCheck(com.zrlog.admin.business.ai.service.AIServiceDependencies.actor(db.dataSource()), detail, new CreateArticleRequest());
                }
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    try (var ignored = AdminLanguageContext.open(language.equals("en_US") ? "zh_CN" : "en_US")) {
                        proceed.countDown();
                        assertEquals(
                                language, task.getFuture().get(5, TimeUnit.SECONDS).getContent());
                    }
                } finally {
                    proceed.countDown();
                }
            }
        }
    }

    @Test
    public void shouldExtractScoresAndItemCountsFromKnownPayloads() throws Exception {
        AIPublishCheckService service =
                new AIPublishCheckService(
                        ApplicationServicesFixture.settings(null),
                        ApplicationServicesFixture.operations(null),
                        AIServiceDependencies.writing(null),
                        AIServiceDependencies.conversations(null),
                        com.zrlog.admin.support.AccountServicesFixture.component(null).identities(),
                        com.zrlog.admin.support.AccountServicesFixture.component(null).permissions());
        Method extractScore = method("extractScore", Object.class);
        Method extractItemCount = method("extractItemCount", Object.class);
        ScoreArticleResponse response = new ScoreArticleResponse();
        response.setScore(88);
        response.setItems(List.of(new ScoreArticleResponse.ScoreItem(), new ScoreArticleResponse.ScoreItem()));
        Map<String, Object> mapPayload = Map.of("score", "76", "items", List.of("a", "b", "c"));

        assertEquals(88, extractScore.invoke(service, response));
        assertEquals(2, extractItemCount.invoke(service, response));
        assertEquals(76, extractScore.invoke(service, mapPayload));
        assertEquals(3, extractItemCount.invoke(service, mapPayload));
        assertNull(extractScore.invoke(service, Map.of("score", "bad")));
        assertNull(extractScore.invoke(service, "unknown"));
        assertEquals(0, extractItemCount.invoke(service, "unknown"));
        response.setItems(null);
        assertEquals(0, extractItemCount.invoke(service, response));
    }

    @Test
    public void shouldConvertIntegerLikeValues() throws Exception {
        AIPublishCheckService service =
                new AIPublishCheckService(
                        ApplicationServicesFixture.settings(null),
                        ApplicationServicesFixture.operations(null),
                        AIServiceDependencies.writing(null),
                        AIServiceDependencies.conversations(null),
                        com.zrlog.admin.support.AccountServicesFixture.component(null).identities(),
                        com.zrlog.admin.support.AccountServicesFixture.component(null).permissions());
        Method method = method("toInteger", Object.class);

        assertEquals(7, method.invoke(service, 7L));
        assertEquals(8, method.invoke(service, "8"));
        assertNull(method.invoke(service, "bad"));
        assertNull(method.invoke(service, new Object[]{null}));
    }

    private Method method(String name, Class<?>... types) throws Exception {
        Method method = AIPublishCheckService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
}
