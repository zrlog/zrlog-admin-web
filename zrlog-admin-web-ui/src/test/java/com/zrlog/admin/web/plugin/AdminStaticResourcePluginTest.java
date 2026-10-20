package com.zrlog.admin.web.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hibegin.http.server.ApplicationContext;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.model.WebSite;

import org.junit.Test;

import java.util.concurrent.ExecutorService;

public class AdminStaticResourcePluginTest {

    @Test
    public void shouldExposeAdminStaticPluginStateWhenStaticSiteIsDisabled() throws Exception {
        try (InMemoryZrLogDatabase database = InMemoryZrLogDatabase.open()) {
            var config = database.config();
            var context = new ApplicationContext(config.getServerConfig());
            context.init();
            AdminStaticResourcePlugin plugin =
                    new AdminStaticResourcePlugin(
                            new StaticSiteRenderer(
                                    config.getRequestConfig(), config.getResponseConfig(), context),
                            database.cacheService()::getPublicWebSiteInfo,
                            database.resources(),
                            "/blog",
                            () -> new WebSite(database.dataSource()));
            try {
                assertFalse(plugin.autoStart());
                assertFalse(plugin.isStarted());

                assertTrue(plugin.start());

                assertFalse(plugin.isStarted());
                assertFalse(plugin.stop());
                assertEquals("admin-version.txt", plugin.getVersionFileName());
                assertEquals("test", plugin.getSiteVersion());
                assertEquals("admin_static_version", plugin.getDbCacheKey());
                assertEquals("/blog", plugin.getContextPath());
                assertEquals("zh_CN", plugin.getDefaultLang());
                assertSame(plugin.getHandleStatusPageMap(), plugin.getHandleStatusPageMap());
                assertNotNull(plugin.getParseLock());
                assertNotNull(plugin.getExecutorService());
                assertTrue(plugin.getCacheFiles().isEmpty());
                assertEquals(StaticSiteType.ADMIN, plugin.getType());
            } finally {
                ((ExecutorService) plugin.getExecutorService()).shutdownNow();
            }
        }
    }
}
