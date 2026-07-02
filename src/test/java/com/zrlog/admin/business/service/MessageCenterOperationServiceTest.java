package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.MessageCenterOperationNoticeEntry;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MessageCenterOperationServiceTest {

    private static final Gson GSON = new Gson();

    @Test
    @SuppressWarnings("unchecked")
    public void shouldTrimOperationNoticesByUpdatedAtDescending() throws Exception {
        MessageCenterOperationService service = newService();
        Method method = method("trimNotices", List.class);
        List<MessageCenterOperationNoticeEntry> notices = notices(55);

        List<MessageCenterOperationNoticeEntry> trimmed =
                (List<MessageCenterOperationNoticeEntry>) method.invoke(service, notices);

        assertEquals(50, trimmed.size());
        assertEquals(Long.valueOf(54), trimmed.get(0).getUpdatedAt());
        assertEquals(Long.valueOf(5), trimmed.get(49).getUpdatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturnSameOperationNoticeListWhenNoTrimNeeded() throws Exception {
        MessageCenterOperationService service = newService();
        Method method = method("trimNotices", List.class);
        List<MessageCenterOperationNoticeEntry> notices = notices(2);

        List<MessageCenterOperationNoticeEntry> trimmed =
                (List<MessageCenterOperationNoticeEntry>) method.invoke(service, notices);

        assertSame(notices, trimmed);
        assertEquals(Long.valueOf(1), trimmed.get(0).getUpdatedAt());
        assertEquals(Long.valueOf(0), trimmed.get(1).getUpdatedAt());
    }

    @Test
    public void shouldNormalizeOperationStatusAndTaskKeys() throws Exception {
        MessageCenterOperationService service = newService();
        Method normalizeStatus = method("normalizeStatus", String.class);
        Method buildTaskKey = method("buildTaskKey", String.class, long.class);

        assertEquals("success", normalizeStatus.invoke(service, "success"));
        assertEquals("warning", normalizeStatus.invoke(service, "warning"));
        assertEquals("error", normalizeStatus.invoke(service, "error"));
        assertEquals("cancelled", normalizeStatus.invoke(service, "cancelled"));
        assertEquals("notice", normalizeStatus.invoke(service, "notice"));
        assertEquals("notice", normalizeStatus.invoke(service, new Object[]{null}));
        String taskKey = (String) buildTaskKey.invoke(service, "upgrade", 123L);
        assertTrue(taskKey.startsWith("server.operation.upgrade.123."));
        assertEquals(8, taskKey.substring(taskKey.lastIndexOf('.') + 1).length());
    }

    @Test
    public void shouldExtractScoresAndItemCountsFromKnownPayloads() throws Exception {
        MessageCenterOperationService service = newService();
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
        MessageCenterOperationService service = newService();
        Method method = method("toInteger", Object.class);

        assertEquals(7, method.invoke(service, 7L));
        assertEquals(8, method.invoke(service, "8"));
        assertNull(method.invoke(service, "bad"));
        assertNull(method.invoke(service, new Object[]{null}));
    }

    @Test
    public void shouldBuildArticleOperationNoticeShell() throws Exception {
        MessageCenterOperationService service = newService();
        Method method = method("buildArticleOperationNotice",
                String.class, long.class, String.class, String.class, String.class, Long.class);

        MessageCenterOperationNoticeEntry notice = (MessageCenterOperationNoticeEntry) method.invoke(service,
                "publishCheck", 123L, "admin.article.title", "description", "warning", 9L);

        assertTrue(notice.getTaskKey().startsWith("server.operation.publishCheck.123."));
        assertEquals("description", notice.getDescription());
        assertEquals("/article-edit?id=9", notice.getActionPath());
        assertEquals("warning", notice.getStatus());
        assertEquals(Boolean.TRUE, notice.getClosable());
        assertEquals(Long.valueOf(123L), notice.getCreatedAt());
        assertEquals(Long.valueOf(123L), notice.getUpdatedAt());
    }

    @Test
    public void shouldRecordListAndMarkOperationNoticesWithCache() throws Exception {
        FakeWebsiteCacheService cache = fakeCache();
        FakeMessageCenterStateService state = fakeState();
        MessageCenterOperationService service = newService(cache, state);
        ScoreArticleResponse response = new ScoreArticleResponse();
        response.setScore(90);
        response.setItems(List.of(new ScoreArticleResponse.ScoreItem()));

        service.recordPublishCheckSuccess(7L, "Article", response);
        List<MessageCenterNoticeResponse> notices = service.listOperationNotices();

        assertTrue(state.mayHaveUnread);
        assertEquals(1, notices.size());
        MessageCenterNoticeResponse notice = notices.get(0);
        assertTrue(notice.getTaskKey().startsWith("server.operation.publishCheck."));
        assertEquals("operationTask", notice.getType());
        assertEquals("success", notice.getStatus());
        MessageCenterNoticeResponse.OperationTaskPayload payload =
                (MessageCenterNoticeResponse.OperationTaskPayload) notice.getPayload();
        assertEquals("/article-edit?id=7", payload.getActionPath());
        assertEquals(Boolean.TRUE, payload.getClosable());
        MessageCenterOperationNoticeEntry.PublishCheckPayload rawPayload =
                (MessageCenterOperationNoticeEntry.PublishCheckPayload) payload.getPayload();
        assertEquals(Long.valueOf(7L), rawPayload.getArticleId());
        assertEquals("Article", rawPayload.getArticleTitle());
        assertEquals(Integer.valueOf(90), rawPayload.getScore());
        assertEquals(Integer.valueOf(1), rawPayload.getItemCount());
        assertTrue(service.markOperationNoticeRead(notice.getTaskKey()));
        assertTrue(state.changed);
        assertEquals(List.of(), service.listOperationNotices());
        assertFalse(service.markOperationNoticeRead("missing"));
    }

    @Test
    public void shouldRecordOperationNoticeVariantsWithCache() throws Exception {
        MessageCenterOperationService service = newService(fakeCache(), fakeState());
        ReplaceArticleResourceUrlResponse replaceResponse = new ReplaceArticleResourceUrlResponse();
        replaceResponse.setScannedArticles(5);
        replaceResponse.setUpdatedArticles(2);
        replaceResponse.setUpdatedFields(3);

        service.recordReplaceArticleResourceUrl(replaceResponse);
        service.recordStaticSiteSync(List.of(StaticSiteType.BLOG, StaticSiteType.ADMIN), false);
        service.recordBlogStaticSiteSync(false, " custom ");
        service.recordUpgradeResult(new UpgradeProcessResponse(false, " wait "));
        service.recordUpgradeError(" boom ");
        service.recordUpgradeRestart("mystery", " build-1 ");
        service.recordPublishCheckError(8L, "Article", "error msg");
        service.recordPublishCheckSuccess(9L, "Warn Article", Map.of("score", "60", "items", List.of("a", "b")));

        List<MessageCenterNoticeResponse> notices = service.listOperationNotices();

        assertEquals(8, notices.size());
        assertTrue(hasPayload(notices, "/file-manager?resourceType=external", "success",
                Map.of("scannedArticles", 5, "updatedArticles", 2, "updatedFields", 3)));
        assertTrue(hasPayload(notices, "/website/upgrade", "warning",
                Map.of("siteTypes", List.of("BLOG", "ADMIN"), "synced", false)));
        assertTrue(hasPayload(notices, "/article", "error",
                Map.of("siteTypes", List.of("BLOG"), "synced", false)));
        assertTrue(hasPayload(notices, "/upgrade", "warning",
                Map.of("finish", false, "message", "wait")));
        assertTrue(hasPayload(notices, "/upgrade", "error",
                Map.of("finish", false, "message", "boom")));
        assertTrue(hasPayload(notices, "/upgrade", "notice",
                Map.of("buildId", "build-1")));
        assertTrue(hasPayload(notices, "/article-edit?id=8", "error",
                Map.of("articleId", 8L, "articleTitle", "Article")));
        assertTrue(hasPayload(notices, "/article-edit?id=9", "warning",
                Map.of("articleId", 9L, "articleTitle", "Warn Article", "score", 60, "itemCount", 2)));
    }

    private static List<MessageCenterOperationNoticeEntry> notices(int count) {
        List<MessageCenterOperationNoticeEntry> notices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MessageCenterOperationNoticeEntry notice = new MessageCenterOperationNoticeEntry();
            notice.setTaskKey("task-" + i);
            notice.setUpdatedAt((long) i);
            notices.add(notice);
        }
        return notices;
    }

    private static boolean hasPayload(List<MessageCenterNoticeResponse> notices, String actionPath, String status,
                                      Map<String, Object> expectedValues) {
        for (MessageCenterNoticeResponse notice : notices) {
            if (!status.equals(notice.getStatus())) {
                continue;
            }
            MessageCenterNoticeResponse.OperationTaskPayload payload =
                    (MessageCenterNoticeResponse.OperationTaskPayload) notice.getPayload();
            if (!actionPath.equals(payload.getActionPath())) {
                continue;
            }
            JsonObject rawPayload = GSON.toJsonTree(payload.getPayload()).getAsJsonObject();
            boolean matches = true;
            for (Map.Entry<String, Object> entry : expectedValues.entrySet()) {
                if (!GSON.toJsonTree(entry.getValue()).equals(rawPayload.get(entry.getKey()))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static MessageCenterOperationService newService() throws Exception {
        return allocate(MessageCenterOperationService.class);
    }

    private static MessageCenterOperationService newService(FakeWebsiteCacheService cacheService,
                                                            FakeMessageCenterStateService stateService)
            throws Exception {
        MessageCenterOperationService service = newService();
        setField(service, "cacheService", cacheService);
        setField(service, "messageCenterStateService", stateService);
        return service;
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = MessageCenterOperationService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        return (T) unsafe().allocateInstance(type);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static FakeWebsiteCacheService fakeCache() throws Exception {
        FakeWebsiteCacheService cache = allocate(FakeWebsiteCacheService.class);
        setField(cache, "values", new HashMap<String, Object>());
        return cache;
    }

    private static FakeMessageCenterStateService fakeState() throws Exception {
        return allocate(FakeMessageCenterStateService.class);
    }

    private static class FakeWebsiteCacheService extends WebsiteCacheService {

        private Map<String, Object> values;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getJson(String key, Type type) {
            Object value = values.get(key);
            if (value instanceof List) {
                return (T) new ArrayList<>((List<?>) value);
            }
            return (T) value;
        }

        @Override
        public boolean putJson(String key, Object value) {
            values.put(key, value);
            return true;
        }
    }

    private static class FakeMessageCenterStateService extends MessageCenterStateService {

        private boolean changed;
        private boolean mayHaveUnread;

        @Override
        public void markChanged() {
            changed = true;
        }

        @Override
        public void markMayHaveUnread() {
            mayHaveUnread = true;
        }
    }
}
