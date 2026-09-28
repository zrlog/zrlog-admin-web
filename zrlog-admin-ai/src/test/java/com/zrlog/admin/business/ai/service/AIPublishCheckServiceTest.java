package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.admin.business.rest.response.AIArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.LoadEditArticleResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.util.I18nUtil;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AIPublishCheckServiceTest {
    @Test public void publishWorkerUsesTheRequestLanguageEvenWhenTheSiteAndLaterRequestDiffer() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.cacheService().getPublicWebSiteInfo().setLanguage("zh_CN");
            for (String language : List.of("en_US", "zh_CN")) {
                var started = new java.util.concurrent.CountDownLatch(1);
                var proceed = new java.util.concurrent.CountDownLatch(1);
                AIPublishCheckService service = new AIPublishCheckService() {
                    @Override PublishCheckResponse buildPublishCheckPayload(Long id, GenerateArticleFieldRequest context,
                            PublishCheckPersistenceGuard guard, AIConversationService conversations) {
                        started.countDown();
                        try { assertTrue(proceed.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { throw new RuntimeException(e); }
                        return new PublishCheckResponse(null, I18nUtil.getCurrentLocale(), "test", List.of());
                    }
                };
                AIArticleGlobalResponse detail = new AIArticleGlobalResponse();
                LoadEditArticleResponse article = new LoadEditArticleResponse(); article.setLogId(1);
                detail.setArticle(article); detail.setAiConfigured(true); detail.setPublishCheckEnabled(true);
                AIPublishCheckService.PublishCheckTask task;
                try (var ignored = AdminLanguageContext.open(language)) {
                    task = service.startPublishCheck(detail, new CreateArticleRequest());
                }
                try {
                    assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    try (var ignored = AdminLanguageContext.open(language.equals("en_US") ? "zh_CN" : "en_US")) {
                        proceed.countDown();
                        assertEquals(language, task.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).getContent());
                    }
                } finally { proceed.countDown(); }
            }
        }
    }
    @Test
    public void shouldExtractScoresAndItemCountsFromKnownPayloads() throws Exception {
        AIPublishCheckService service = new AIPublishCheckService();
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
        AIPublishCheckService service = new AIPublishCheckService();
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
