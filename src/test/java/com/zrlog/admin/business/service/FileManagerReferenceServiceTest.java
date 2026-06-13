package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.FileReferenceIndexCacheVO;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
}
