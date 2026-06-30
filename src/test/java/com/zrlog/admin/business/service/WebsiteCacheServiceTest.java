package com.zrlog.admin.business.service;

import com.google.gson.reflect.TypeToken;
import com.zrlog.admin.business.rest.response.WebsiteKvEntryResponse;
import com.zrlog.business.service.WebsiteKvService;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebsiteCacheServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadAndWriteJsonWithAdminCachePrefix() throws Exception {
        FakeWebsiteKvService kv = new FakeWebsiteKvService();
        kv.values.put("admin_cache:config", "{\"enabled\":true}");
        kv.values.put("admin_cache:list", "[{\"name\":\"one\"}]");
        kv.values.put("admin_cache:bad", "{");
        WebsiteCacheService service = service(kv);
        Type listType = new TypeToken<List<Map<String, Object>>>() {
        }.getType();

        Map<String, Object> config = service.getJson("config", Map.class);
        assertEquals("admin_cache:config", kv.lastGetKey);
        List<Map<String, Object>> list = service.getJson("list", listType);
        boolean saved = service.putJson("settings", Map.of("count", 2));

        assertEquals(true, config.get("enabled"));
        assertEquals("admin_cache:list", kv.lastGetKey);
        assertEquals("one", list.get(0).get("name"));
        assertNull(service.getJson("missing", Map.class));
        assertNull(service.getJson("bad", Map.class));
        assertTrue(saved);
        assertEquals("admin_cache:settings", kv.lastPutKey);
        assertEquals("{\"count\":2}", kv.values.get("admin_cache:settings"));
        assertTrue(service.remove("settings"));
        assertEquals("admin_cache:settings", kv.lastRemoveKey);
        assertFalse(service.putJson("fail", Map.of("count", 1)));
    }

    @Test
    public void shouldListCachedWebsiteKvEntries() throws Exception {
        FakeWebsiteKvService kv = new FakeWebsiteKvService();
        kv.rows = List.of(
                Map.of("name", "admin_cache:a", "value", "one", "size", 3),
                Map.of("name", "admin_cache:b", "value", "two", "size", "4")
        );
        WebsiteCacheService service = service(kv);

        List<WebsiteKvEntryResponse> entries = service.listEntries();

        assertEquals(2, entries.size());
        assertEquals("admin_cache:", kv.lastListPrefix);
        assertEquals("admin_cache:a", entries.get(0).getKey());
        assertEquals("one", entries.get(0).getValue());
        assertEquals(3L, entries.get(0).getSize());
        assertEquals("admin_cache:b", entries.get(1).getKey());
        assertEquals(4L, entries.get(1).getSize());
    }

    private static WebsiteCacheService service(FakeWebsiteKvService kvService) throws Exception {
        WebsiteCacheService service = new WebsiteCacheService();
        Field field = WebsiteCacheService.class.getDeclaredField("kvService");
        field.setAccessible(true);
        field.set(service, kvService);
        return service;
    }

    private static class FakeWebsiteKvService extends WebsiteKvService {

        private final Map<String, String> values = new HashMap<>();
        private List<Map<String, Object>> rows = List.of();
        private String lastGetKey;
        private String lastPutKey;
        private String lastRemoveKey;
        private String lastListPrefix;

        @Override
        public String getString(String key) {
            lastGetKey = key;
            return values.get(key);
        }

        @Override
        public boolean putStringQuietly(String key, String value) {
            lastPutKey = key;
            if (key.endsWith("fail")) {
                return false;
            }
            values.put(key, value);
            return true;
        }

        @Override
        public boolean removeQuietly(String key) {
            lastRemoveKey = key;
            values.remove(key);
            return true;
        }

        @Override
        public List<Map<String, Object>> listByPrefix(String prefix) {
            lastListPrefix = prefix;
            return rows;
        }
    }
}
