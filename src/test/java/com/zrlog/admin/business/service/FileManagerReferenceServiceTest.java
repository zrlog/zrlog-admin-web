package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileManagerReferenceServiceTest {

    @Test
    public void shouldReturnFalseWhenReferenceIndexCacheWriteFails() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean putJson(String key, Object value) {
                return false;
            }
        });

        assertFalse(service.writeReferenceIndexCache(new FileReferenceIndexCacheVO()));
    }

    @Test
    public void shouldReturnFalseWhenReferenceIndexCacheClearFails() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean remove(String key) {
                return false;
            }
        });

        assertFalse(service.clearReferenceIndexCache());
    }

    @Test
    public void shouldReturnTrueWhenReferenceIndexCacheWriteSucceeds() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService() {
            @Override
            public boolean putJson(String key, Object value) {
                return true;
            }
        });

        assertTrue(service.writeReferenceIndexCache(new FileReferenceIndexCacheVO()));
    }

    @Test
    public void shouldNormalizeContextPathLocalResource() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");

        assertEquals("/attached/image/a.png", service.normalizeLocalResourcePath("/blog/attached/image/a.png?v=1"));
        assertNull(service.normalizeLocalResourcePath("https://cdn.example.com/attached/image/a.png"));
    }

    @Test
    public void shouldReplaceContextPathLocalResourceReference() {
        FileManagerReferenceService service = new FileManagerReferenceService(new WebsiteCacheService(), "/blog");
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("markdown", "![cover](/blog/attached/image/a.png?v=1)");

        Map<String, Object> updates = service.buildArticleResourceUrlUpdates(
                article, "/attached/image/a.png", "/attached/image/b.png", false);

        assertEquals("![cover](/blog/attached/image/b.png?v=1)", updates.get("markdown"));
    }
}
