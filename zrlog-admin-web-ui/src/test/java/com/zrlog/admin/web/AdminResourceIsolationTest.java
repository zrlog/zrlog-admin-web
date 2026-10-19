package com.zrlog.admin.web;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminPageService;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.AdminResourceProvider;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.AdminPublicController;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.web.WebSiteInfoProvider;
import org.jsoup.Jsoup;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class AdminResourceIsolationTest {
    @Test
    public void otherApplicationsAndSiteRefreshCannotReplaceAnApplicationsResources() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            AdminTokenThreadLocal.remove();
            db.putWebsite("admin_static_resource_base_url", "https://cdn.example");
            TestApplication first = new TestApplication("/first", "First site", 10, true);
            AdminResource firstResources = first.ui.getAdminResource();
            String firstVersion = firstResources.getStaticResourceBuildId();
            // Resolve the first app before creating another UI app or an API-only app.
            assertPage(first, "First site", firstVersion);

            TestApplication second = new TestApplication("/second", "Second site", 100, true);
            String secondVersion = second.ui.getAdminResource().getStaticResourceBuildId();
            TestApplication apiOnly = new TestApplication("/api-only", "API site", 200, false);
            assertEquals("", apiOnly.getServerConfig().getBindings().build()
                    .get(AdminPageDataFactory.class).metadata("/admin/index").getPageBuildId());
            assertNull(apiOnly.getServerConfig().getRouter().getMethod("/admin", HttpMethod.GET));
            assertEquals("API site", publicResources(apiOnly).getWebsiteTitle());

            assertPage(first, "First site", firstVersion);
            assertPage(second, "Second site", secondVersion);
            assertWorker(first, "/first", "/second");
            assertWorker(second, "/second", "/first");

            first.cache.version++;
            first.cache.getPublicWebSiteInfo().setTitle("First site refreshed");
            String refreshedVersion = firstResources.getStaticResourceBuildId();
            assertNotEquals(firstVersion, refreshedVersion);
            assertPage(first, "First site refreshed", refreshedVersion);
            assertPage(second, "Second site", secondVersion);
        }
    }

    private static void assertPage(TestApplication app, String title, String version) throws Throwable {
        var pages = app.getServerConfig().getBindings().build().get(AdminPageService.class);
        HttpRequest request = request(app);
        var page = pages.serverSide("/admin/index", request, response());
        assertEquals(title, page.getResourceInfo().getWebsiteTitle());
        assertEquals(new Gson().toJson(publicResources(app)), new Gson().toJson(page.getResourceInfo()));
        assertEquals(version, app.getServerConfig().getBindings().build()
                .get(AdminPageDataFactory.class).metadata("/admin/index").getPageBuildId());
        String source = "<html><head><base href='/'>"
                + "<link rel='manifest' href='/admin/manifest.json'>"
                + "<script src='/admin/static/app.js'></script></head>"
                + "<body><pre id='__SS_DATA__'></pre></body></html>";
        var document = Jsoup.parse(pages.buildHtml(request, response(),
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))));
        assertTrue(document.title().contains(title));
        assertEquals(app.contextPath + "/", document.selectFirst("base").attr("href"));
        assertEquals("https://cdn.example" + app.contextPath + "/admin/manifest.json?v=" + version,
                document.selectFirst("link[rel=manifest]").attr("href"));
        assertEquals("https://cdn.example" + app.contextPath + "/admin/static/app.js",
                document.selectFirst("script").attr("src"));
    }

    private static com.zrlog.admin.business.rest.response.AdminResourceInfoResponse publicResources(
            TestApplication app) throws Exception {
        var method = AdminPublicController.class.getMethod("adminResource");
        var controller = (AdminPublicController) Controller.buildController(method, request(app), response());
        return controller.adminResource().getData();
    }

    private static void assertWorker(TestApplication app, String ownPath, String otherPath) throws Exception {
        AdminResource resources = app.ui.getAdminResource();
        assertTrue(resources.getAdminPageUris().contains(ownPath + "/admin/index"));
        assertFalse(resources.getAdminPageUris().contains(otherPath + "/admin/index"));
        try (var worker = resources.renderServiceWorker(request(app))) {
            String script = new String(worker.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(script.contains("https://cdn.example" + ownPath + "/admin/static/"));
            assertFalse(script.contains("https://cdn.example" + otherPath + "/"));
            assertFalse(script.contains(ownPath + "/admin/index?"));
        }
    }

    private static HttpRequest request(TestApplication app) {
        return (HttpRequest) Proxy.newProxyInstance(AdminResourceIsolationTest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContextPath": return app.contextPath;
                        case "getUri": return "/admin/index";
                        case "getServerConfig": return app.getServerConfig();
                        case "getHeader": return "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap": return Map.of();
                        case "getScheme": return "http";
                        case "getRemoteHost": return "127.0.0.1";
                        default: return null;
                    }
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(AdminResourceIsolationTest.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> null);
    }

    private static class TestApplication extends ZrLogConfig {
        private final String contextPath;
        private final SiteCache cache;
        private final AdminUiWebSetup ui;

        TestApplication(String contextPath, String title, long version, boolean withUi) {
            super(18080, null, contextPath);
            this.contextPath = contextPath;
            cache = new SiteCache();
            cache.version = version;
            cache.getPublicWebSiteInfo().setTitle(title);
            getServerConfig().getBindings().instance(CacheService.class, cache);
            new AdminWebSetup(this).setup();
            ui = withUi ? new AdminUiWebSetup(this, contextPath) : null;
            if (ui != null) ui.setup();
        }

        @Override public boolean isInstalled() { return cache != null; }

        @Override public java.util.List<com.zrlog.plugin.IPlugin> getBasePluginList() {
            return java.util.Collections.emptyList();
        }

        @Override public <T> T getWebSetup(Class<T> type) {
            if (type == WebSiteInfoProvider.class) return type.cast((WebSiteInfoProvider) cache::getPublicWebSiteInfo);
            if (type == AdminResourceProvider.class) return type.cast(ui);
            return super.getWebSetup(type);
        }
    }

    private static class SiteCache extends InMemoryZrLogDatabase.TestCacheService {
        private long version;

        @Override public long getWebSiteVersion() { return version; }
    }
}
