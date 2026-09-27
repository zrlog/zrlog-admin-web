package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AIPublishCheckServiceTest {
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
