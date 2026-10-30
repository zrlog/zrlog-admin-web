package com.zrlog.admin.web.plugin;

import com.hibegin.http.server.ApplicationContext;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.model.WebSite;
import org.junit.Test;

import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

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
                            database.unitCacheService()::getPublicWebSiteInfo,
                            database.resources(),
                            "/blog",
                            () -> new WebSite(database.dataSource()));
            try {
                assertFalse(plugin.autoStart());
                assertFalse(plugin.isStarted());

                assertTrue(plugin.start());
                assertTrue(plugin.startWithinBudget(new com.zrlog.business.plugin.StaticGenerationBudget(
                        com.zrlog.util.ExecutionDeadline.afterMillis(5000), () -> {})));

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

    @Test
    public void boundedGenerationRendersAdminAssetsAndUsesTheBuildIdVersion() throws Exception {
        try (InMemoryZrLogDatabase database = InMemoryZrLogDatabase.open()) {
            var config = database.config();
            config.getServerConfig().getInterceptors().clear();
            config.getServerConfig().addInterceptor(StaticPages.class);
            var context = new ApplicationContext(config.getServerConfig());
            context.init();
            com.zrlog.common.vo.PublicWebSiteInfo site = new com.zrlog.common.vo.PublicWebSiteInfo();
            site.setGenerator_html_status(true);
            site.setHost("admin.example.com");
            var resources = (com.zrlog.admin.business.service.AdminResource) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{com.zrlog.admin.business.service.AdminResource.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getAdminStaticCacheUris")) return java.util.Set.of("/admin/index");
                        if (method.getName().equals("getAdminStaticResourceUris")) return java.util.Set.of("/admin/static/app.js");
                        return method.invoke(database.resources(), args);
                    });
            var plugin = new AdminStaticResourcePlugin(new StaticSiteRenderer(config.getRequestConfig(), config.getResponseConfig(), context),
                    () -> site, resources, "", () -> new WebSite(database.dataSource()));
            try {
                ((ExecutorService) plugin.getExecutorService()).shutdownNow();
                var budget = new com.zrlog.business.plugin.StaticGenerationBudget(
                        com.zrlog.util.ExecutionDeadline.afterMillis(5000), () -> {});
                assertTrue(plugin.startWithinBudget(budget));
                assertFalse(plugin.getHandleStatusPageMap().isEmpty());
                assertTrue(plugin.getCacheFile("/admin/index.html").isFile());
                assertTrue(plugin.getCacheFile("/admin/static/app.js").isFile());
                assertTrue(plugin.getHandleStatusPageMap().values().stream().allMatch(
                        state -> state == com.zrlog.business.plugin.StaticSitePlugin.HandleState.HANDLED));
                assertEquals("test", plugin.getSiteVersionWithinBudget(budget));
                assertEquals("test", java.nio.file.Files.readString(plugin.getCacheFile(plugin.getVersionFileName()).toPath()));
                assertFalse(plugin.isStarted());
            } finally { ((ExecutorService) plugin.getExecutorService()).shutdownNow(); }
        }
    }

    public static class StaticPages implements com.hibegin.http.server.api.Interceptor {
        @Override public boolean doInterceptor(com.hibegin.http.server.api.HttpRequest request,
                                               com.hibegin.http.server.api.HttpResponse response) {
            response.renderHtmlStr("<main>admin</main>");
            return false;
        }
    }
}
