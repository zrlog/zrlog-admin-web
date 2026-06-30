package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.request.WebhookConfigRequest;
import com.zrlog.admin.business.rest.request.WebhookMessageNoticeRequest;
import com.zrlog.admin.business.rest.response.MessageCenterNoticeResponse;
import com.zrlog.admin.business.rest.response.WebhookConfigEntry;
import com.zrlog.admin.business.rest.response.WebhookConfigResponse;
import com.zrlog.admin.business.rest.response.WebhookMessageNoticeEntry;
import com.zrlog.admin.business.rest.response.WebhookMessageNoticeCreateResponse;
import com.zrlog.admin.business.rest.response.WebhookTokenResponse;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WebhookServiceTest {

    @Test
    public void shouldNormalizeWebhookTaskKeys() throws Exception {
        WebhookService service = newService();
        Method method = method("normalizeTaskKey", String.class);

        assertEquals("server.webhook.message.job-1", method.invoke(service, " job-1 "));
        assertEquals("server.custom.job", method.invoke(service, "server.custom.job"));
        assertTrue(((String) method.invoke(service, new Object[]{null})).startsWith("server.webhook.message."));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldTrimWebhookNoticesByUpdatedAtDescending() throws Exception {
        WebhookService service = newService();
        Method method = method("trimNotices", List.class);
        List<WebhookMessageNoticeEntry> notices = notices(55);

        List<WebhookMessageNoticeEntry> trimmed = (List<WebhookMessageNoticeEntry>) method.invoke(service, notices);

        assertEquals(50, trimmed.size());
        assertEquals(Long.valueOf(54), trimmed.get(0).getUpdatedAt());
        assertEquals(Long.valueOf(5), trimmed.get(49).getUpdatedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturnSameWebhookNoticeListWhenNoTrimNeeded() throws Exception {
        WebhookService service = newService();
        Method method = method("trimNotices", List.class);
        List<WebhookMessageNoticeEntry> notices = notices(2);

        List<WebhookMessageNoticeEntry> trimmed = (List<WebhookMessageNoticeEntry>) method.invoke(service, notices);

        assertSame(notices, trimmed);
        assertEquals(Long.valueOf(1), trimmed.get(0).getUpdatedAt());
        assertEquals(Long.valueOf(0), trimmed.get(1).getUpdatedAt());
    }

    @Test
    public void shouldBuildWebhookConfigResponse() throws Exception {
        WebhookService service = newService();
        Method method = method("toConfigResponse", WebhookConfigEntry.class);
        WebhookConfigEntry disabled = new WebhookConfigEntry();
        WebhookConfigEntry enabled = new WebhookConfigEntry();
        enabled.setEnabled(true);
        enabled.setTokenHash("hash");
        enabled.setTokenPreview("abcd...wxyz");
        enabled.setTokenUpdatedAt(123L);

        WebhookConfigResponse disabledResponse = (WebhookConfigResponse) method.invoke(service, disabled);
        WebhookConfigResponse enabledResponse = (WebhookConfigResponse) method.invoke(service, enabled);

        assertFalse(disabledResponse.getEnabled());
        assertFalse(disabledResponse.getHasToken());
        assertEquals(WebhookService.MESSAGE_CENTER_NOTICE_ENDPOINT, disabledResponse.getEndpoint());
        assertEquals(WebhookService.TOKEN_HEADER, disabledResponse.getTokenHeader());
        assertTrue(enabledResponse.getEnabled());
        assertTrue(enabledResponse.getHasToken());
        assertEquals("abcd...wxyz", enabledResponse.getTokenPreview());
        assertEquals(Long.valueOf(123L), enabledResponse.getTokenUpdatedAt());
    }

    @Test
    public void shouldPreviewAndHashWebhookTokens() throws Exception {
        WebhookService service = newService();
        Method previewToken = method("previewToken", String.class);
        Method sha256 = method("sha256", String.class);

        assertEquals("12345678", previewToken.invoke(service, "12345678"));
        assertEquals("abcd...ijkl", previewToken.invoke(service, "abcdefghijkl"));
        assertEquals("3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0",
                sha256.invoke(service, "token"));
    }

    @Test
    public void shouldManageWebhookConfigAndTokensWithCache() throws Exception {
        FakeWebsiteCacheService cache = fakeCache();
        WebhookService service = newService(cache, fakeState());
        WebhookConfigRequest request = new WebhookConfigRequest();
        request.setEnabled(true);

        WebhookConfigResponse initial = service.getConfigResponse();
        WebhookConfigResponse updated = service.updateConfig(request);
        WebhookTokenResponse tokenResponse = service.rotateToken();

        assertFalse(initial.getEnabled());
        assertFalse(initial.getHasToken());
        assertTrue(updated.getEnabled());
        assertFalse(updated.getHasToken());
        assertEquals(43, tokenResponse.getToken().length());
        assertTrue(tokenResponse.getConfig().getEnabled());
        assertTrue(tokenResponse.getConfig().getHasToken());
        assertTrue(service.verifyToken(tokenResponse.getToken()));
        assertFalse(service.verifyToken("bad-token"));
        WebhookConfigResponse revoked = service.revokeToken();
        assertFalse(revoked.getEnabled());
        assertFalse(revoked.getHasToken());
        assertFalse(service.verifyToken(tokenResponse.getToken()));
        assertTrue(cache.values.containsKey("webhook_config"));
    }

    @Test
    public void shouldCreateListAndMarkWebhookMessageNoticesWithCache() throws Exception {
        FakeWebsiteCacheService cache = fakeCache();
        FakeMessageCenterStateService state = fakeState();
        WebhookService service = newService(cache, state);
        WebhookMessageNoticeRequest request = new WebhookMessageNoticeRequest();
        request.setTaskKey("deploy");
        request.setTitle("Deploy done");
        request.setDescription("Finished");
        request.setActionLabel("Open");
        request.setActionPath("/admin");
        request.setSource("server");
        request.setClosable(false);
        request.setUpdatedAt(99L);
        request.setPayload(Map.of("env", "prod"));

        WebhookMessageNoticeCreateResponse created = service.createMessageCenterNotice(request);
        List<MessageCenterNoticeResponse> notices = service.listMessageCenterNotices();

        assertEquals("server.webhook.message.deploy", created.getTaskKey());
        assertEquals(Long.valueOf(99L), created.getUpdatedAt());
        assertTrue(state.mayHaveUnread);
        assertEquals(1, notices.size());
        MessageCenterNoticeResponse notice = notices.get(0);
        assertEquals(created.getTaskKey(), notice.getTaskKey());
        assertEquals("webhookMessage", notice.getType());
        assertEquals("notice", notice.getStatus());
        assertEquals(Long.valueOf(99L), notice.getUpdatedAt());
        MessageCenterNoticeResponse.WebhookMessagePayload payload =
                (MessageCenterNoticeResponse.WebhookMessagePayload) notice.getPayload();
        assertEquals("Deploy done", payload.getTitle());
        assertEquals("Finished", payload.getDescription());
        assertEquals(Boolean.FALSE, payload.getClosable());
        assertEquals(Map.of("env", "prod"), payload.getPayload());
        assertTrue(service.markMessageCenterNoticeRead(created.getTaskKey()));
        assertTrue(state.changed);
        assertEquals(List.of(), service.listMessageCenterNotices());
        assertFalse(service.markMessageCenterNoticeRead("missing"));
    }

    private static List<WebhookMessageNoticeEntry> notices(int count) {
        List<WebhookMessageNoticeEntry> notices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WebhookMessageNoticeEntry notice = new WebhookMessageNoticeEntry();
            notice.setTaskKey("task-" + i);
            notice.setUpdatedAt((long) i);
            notices.add(notice);
        }
        return notices;
    }

    private static WebhookService newService() throws Exception {
        return allocate(WebhookService.class);
    }

    private static WebhookService newService(FakeWebsiteCacheService cacheService,
                                             FakeMessageCenterStateService stateService) throws Exception {
        WebhookService service = newService();
        setField(service, "cacheService", cacheService);
        setField(service, "messageCenterStateService", stateService);
        return service;
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = WebhookService.class.getDeclaredMethod(name, parameterTypes);
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
        public <T> T getJson(String key, Class<T> clazz) {
            return (T) values.get(key);
        }

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
