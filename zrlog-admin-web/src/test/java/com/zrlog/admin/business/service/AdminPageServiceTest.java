package com.zrlog.admin.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.ProxySettingsResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.rest.response.SystemResponse;
import com.zrlog.admin.business.rest.response.UserPreferencesResponse;
import com.zrlog.admin.business.rest.response.VersionResponse;
import com.zrlog.admin.business.security.MemberModels;
import com.zrlog.admin.business.security.OAuthModels;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.config.AdminAccountPages;
import com.zrlog.admin.web.controller.api.WebSiteController;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.ui.AdminUiComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.WebSite;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.NetworkProxy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class AdminPageServiceTest {

    private ZrLogConfig applicationConfig;

    private AdminResource resources;

    @Before
    public void setUp() {
        resources = new FakeAdminResource();
        AdminTokenThreadLocal.remove();
    }

    @After
    public void tearDown() {
        AdminTokenThreadLocal.remove();
    }

    @Test
    public void shouldBuildServerSideDataForAnonymousRequest() throws Throwable {
        ServerSideDataResponse<Object> response = pageService(new InMemoryZrLogDatabase.TestCacheService(), null).serverSide(
                "/admin/index", request("/admin/index", "/blog"), response());

        assertNull(response.getUser());
        assertNull(response.getData());
        assertNull(response.getKey());
        assertNotNull(response.getResourceInfo());
        assertEquals("3.6.0", response.getResourceInfo().getCurrentVersion());
    }

    @Test
    public void backendAddressIsAbsentFromAnonymousAndStaticPageData() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.putWebsite("backend_server_url", "https://private-setting.example/sub");
            resources = new AdminResourceImpl("", () -> 0L, resourceService(db.dataSource())::staticResourceBaseUrl);
            AdminTokenThreadLocal.remove();
            for (String userAgent :
                    java.util.List.of("Browser", BaseStaticSitePlugin.STATIC_USER_AGENT)) {
                HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getHeader") && "User-Agent".equals(args[0])) return userAgent;
                            return method.invoke(request("/admin/website/admin", ""), args);
                        });
                String data = new com.google.gson.Gson().toJson(pageService(db.unitCacheService(), db.dataSource()).serverSide("/admin/website/admin", request, response()));
                assertFalse(data.contains("backend_server_url"));
                assertFalse(data.contains("private-setting.example"));
            }
        }
    }

    @Test
    public void shouldGenerateProxyAndDeveloperShellsWithPublicResourcesOnly() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            AccountAuthorizationTest.login(db, 1, "owner");
            for (String contextPath : java.util.List.of("", "/blog")) {
                resources = new AdminResourceImpl(contextPath, () -> 0L, resourceService(db.dataSource())::staticResourceBaseUrl);
                for (String page : java.util.List.of("/admin/system/proxy", "/admin/dev", "/admin/dev/ui")) {
                    assertTrue(resources.getAdminStaticCacheUris().contains(contextPath + page));
                    HttpRequest request =
                            (HttpRequest)
                                    Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class[] {HttpRequest.class},
                                            (proxy, method, args) -> {
                                                if (method.getName().equals("getHeader")) {
                                                    if ("User-Agent".equals(args[0]))
                                                        return BaseStaticSitePlugin
                                                                .STATIC_USER_AGENT;
                                                    if ("Host".equals(args[0]))
                                                        return "localhost:18080";
                                                }
                                                if (method.getName().equals("getScheme"))
                                                    return "http";
                                                return method.invoke(
                                                        request(page, contextPath), args);
                                            });
                    String rendered = db.component(AdminUiComponent.class).pages().buildHtml(request, response(), html(
                            "<html><head><base href=\"/\"><link rel=\"manifest\" href=\"/admin/manifest.json\">"
                                    + "<script src=\"/admin/static/app.js\"></script></head>"
                                    + "<body><div id=\"__SS_DATA__\"></div></body></html>"));
                    Document document = Jsoup.parse(rendered);
                    com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(
                            document.getElementById("__SS_DATA__").text()).getAsJsonObject();
                    assertEquals(
                            AdminConstants.getAdminDocumentTitleByUri(
                                    page, ApplicationServicesFixture.site(db.config()).get()),
                            document.title());
                    assertEquals(contextPath + "/", document.selectFirst("base").attr("href"));
                    assertEquals(contextPath + "/admin/static/app.js", document.selectFirst("script").attr("src"));
                    assertTrue(data.getAsJsonObject("resourceInfo").get("staticPage").getAsBoolean());
                    assertFalse(data.has("user"));
                    assertFalse(data.has("data"));
                    assertFalse(data.has("key"));
                }
            }
        }
    }

    @Test
    public void shouldInjectUiReviewReadinessAndPersonalAppearanceIntoTheFirstPage() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            String page = "/admin/dev/ui";
            for (String contextPath : java.util.List.of("", "/blog")) {
                resources = new AdminResourceImpl(contextPath, () -> 0L, resourceService(db.dataSource())::staticResourceBaseUrl);
                AdminPageService service = db.component(AdminUiComponent.class).pages();
                for (String role : java.util.List.of("owner", "admin")) {
                    AccountAuthorizationTest.login(db, 1, role);
                    db.component(AccountComponent.class).preferences()
                            .updateBody(
                                    "{\"language\":\"en_US\",\"appearance\":{"
                                            + "\"theme\":\"antd\",\"darkMode\":true,\"compactMode\":true,\"colorPrimary\":\"#00875a\"}}");
                    Document document = Jsoup.parse(service.buildHtml(request(page, contextPath, config), response(),
                            html("<html><head><base href=\"/\"></head><body><pre id=\"__SS_DATA__\"></pre></body></html>")));
                    com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(
                            document.getElementById("__SS_DATA__").text()).getAsJsonObject();
                    assertTrue(data.get("data").getAsBoolean());
                    assertEquals(1, data.getAsJsonObject("user").get("userId").getAsInt());
                    assertEquals(AdminTokenThreadLocal.getUser().getSessionId(), data.get("key").getAsString());
                    com.google.gson.JsonObject resource = data.getAsJsonObject("resourceInfo");
                    assertEquals("antd", resource.get("admin_theme").getAsString());
                    assertEquals("en_US", resource.get("lang").getAsString());
                    assertEquals(resource.get("lang"), data.getAsJsonObject("user").get("language"));
                    assertEquals(resource.get("admin_theme"), data.getAsJsonObject("user").getAsJsonObject("appearance").get("theme"));
                    assertEquals("#00875a", resource.get("admin_color_primary").getAsString());
                    assertTrue(resource.get("admin_darkMode").getAsBoolean());
                    assertTrue(resource.get("admin_compactMode").getAsBoolean());
                    assertFalse(resource.get("staticPage").getAsBoolean());
                    assertTrue(document.body().hasClass("dark"));
                    assertEquals(contextPath + "/", document.selectFirst("base").attr("href"));
                    assertEquals(
                            AdminConstants.getAdminDocumentTitleByUri(
                                    page, ApplicationServicesFixture.site(db.config()).get()),
                            document.title());
                    assertEquals(Boolean.TRUE, service.serverSide(page,
                            request("/admin/ssJson", contextPath, config), response()).getData());
                }
                for (String role : java.util.List.of("editor", "author", "contributor")) {
                    AccountAuthorizationTest.login(db, 1, role);
                    for (String uri : java.util.List.of(page, "/admin/ssJson")) {
                        org.junit.Assert.assertThrows(
                                PermissionErrorException.class,
                                () ->
                                        service.serverSide(
                                                page,
                                                request(uri, contextPath, config),
                                                response()));
                    }
                }
            }
        }
    }

    @Test
    public void shouldRewriteAdminHtmlForNormalPage() throws Throwable {
        String html = pageService(new InMemoryZrLogDatabase.TestCacheService(), null).buildHtml(
                request("/admin/index", "/blog"),
                response(),
                html("<html><head>"
                        + "<base href=\"/old/\">"
                        + "<meta name=\"theme-color\" content=\"#000000\">"
                        + "<link rel=\"manifest\" href=\"/admin/manifest.json\">"
                        + "<link rel=\"stylesheet\" href=\"/admin/static/app.css\">"
                        + "<link rel=\"shortcut icon\" href=\"/favicon.ico\">"
                        + "<script src=\"/admin/static/app.js\"></script>"
                        + "</head><body class=\"dark\"><div id=\"__SS_DATA__\"></div></body></html>"));

        Document document = Jsoup.parse(html);

        assertTrue(document.body().hasClass("light"));
        assertFalse(document.body().hasClass("dark"));
        assertEquals("/blog/", document.selectFirst("base").attr("href"));
        assertEquals("/blog/admin/manifest.json?v=build-1",
                document.selectFirst("link[rel=manifest]").attr("href"));
        assertEquals("/blog/admin/static/app.css",
                document.selectFirst("link[rel=stylesheet]").attr("href"));
        assertEquals("/blog/favicon.ico?v=build-1",
                document.selectFirst("link[rel=shortcut icon]").attr("href"));
        assertEquals("/blog/admin/static/app.js", document.selectFirst("script").attr("src"));
        assertEquals("build-1", resources.getStaticResourceBuildId());
        assertTrue(document.getElementById("__SS_DATA__").text().contains("3.6.0"));
    }

    @Test
    public void shouldRemoveManifestOnLoginPage() throws Throwable {
        String html = pageService(new InMemoryZrLogDatabase.TestCacheService(), null).buildHtml(
                request("/admin/login", "/"),
                response(),
                html("<html><head>"
                        + "<base href=\"/old/\">"
                        + "<link rel=\"manifest\" href=\"/admin/manifest.json\">"
                        + "</head><body><div id=\"__SS_DATA__\"></div></body></html>"));

        Document document = Jsoup.parse(html);

        assertTrue(document.select("link[rel=manifest]").isEmpty());
        assertEquals("/", document.selectFirst("base").attr("href"));
    }

    @Test
    public void shouldRewriteStaticResourcesWithConfiguredCdnHostFromRealWebsiteTable() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            AdminTokenThreadLocal.remove();
            db.putWebsite("admin_static_resource_base_url", "https://cdn.example");

            String html = pageService(db.unitCacheService(), db.dataSource()).buildHtml(
                    request("/admin/index", "/blog"),
                    response(),
                    html("<html><head>"
                            + "<base href=\"/old/\">"
                            + "<link rel=\"manifest\" href=\"/admin/manifest.json\">"
                            + "<link rel=\"stylesheet\" href=\"/admin/static/app.css\">"
                            + "<link rel=\"shortcut icon\" href=\"/favicon.ico\">"
                            + "<script src=\"/admin/static/app.js\"></script>"
                            + "</head><body><div id=\"__SS_DATA__\"></div></body></html>"));

            Document document = Jsoup.parse(html);

            assertEquals("https://cdn.example/blog/admin/manifest.json?v=test",
                    document.selectFirst("link[rel=manifest]").attr("href"));
            assertEquals("https://cdn.example/blog/admin/static/app.css",
                    document.selectFirst("link[rel=stylesheet]").attr("href"));
            assertEquals("https://cdn.example/blog/favicon.ico?v=test",
                    document.selectFirst("link[rel=shortcut icon]").attr("href"));
            assertEquals("https://cdn.example/blog/admin/static/app.js",
                    document.selectFirst("script").attr("src"));
        }
    }

    @Test
    public void shouldBuildServerSideDataForAdminPageResponseThroughRouterAndRealUser() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            resources = ignored.resources();
            setAdminToken();
            RequestConfig requestConfig = requestConfig(ignored.dataSource());

            ServerSideDataResponse<Object> response = pageService(ignored.unitCacheService(), ignored.dataSource()).serverSide(
                    "/admin/ssr/page", request("/admin/ssr/page", "/blog", requestConfig), response());

            assertEquals("admin", response.getUser().getUserName());
            assertEquals("session-1", response.getKey());
            assertEquals("page-data", response.getData());
            assertNotNull(response.getDocumentTitle());
        }
    }

    @Test
    public void shouldKeepAuthenticatedShellWhenFeatureRouteIsNotRegistered() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            resources = ignored.resources();
            setAdminToken();
            RequestConfig config = new RequestConfig();
            config.setRouter(new Router());
            for (String uri : java.util.List.of("/admin/website/ai", "/admin/user/applications/tokens")) {
                ServerSideDataResponse<Object> data = pageService(ignored.unitCacheService(), ignored.dataSource()).serverSide(
                        uri, request(uri, "/blog", config), response());
                assertEquals("admin", data.getUser().getUserName());
                assertEquals("session-1", data.getKey());
                assertNotNull(data.getResourceInfo());
                assertNull(data.getData());
            }
        }
    }

    @Test
    public void shouldUseEmptyObjectWhenServerSideControllerReturnsNull() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            resources = ignored.resources();
            setAdminToken();
            RequestConfig requestConfig = requestConfig(ignored.dataSource());

            ServerSideDataResponse<Object> response = pageService(ignored.unitCacheService(), ignored.dataSource()).serverSide(
                    "/admin/ssr/empty", request("/admin/ssr/empty", "/blog", requestConfig), response());

            assertNotNull(response.getData());
            assertEquals(Object.class, response.getData().getClass());
        }
    }

    @Test
    public void shouldHydrateAccountSecurityWithOnlyTheCurrentUsersPasskeySummaries() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            AccountAuthorizationTest.login(db, 1, "owner");
            db.loadAdminWebModules();
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            AdminPageService service = db.component(AdminUiComponent.class).pages();
            String page = "/admin/user/security";
            com.google.gson.Gson gson = new com.google.gson.Gson();

            com.google.gson.JsonObject empty = gson.toJsonTree(service.serverSide(
                    page, request(page, "/blog", config), response()).getData()).getAsJsonObject();
            assertEquals(0, empty.getAsJsonArray("passkeys").size());

            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "writer", "author");
            UserPasskey passkeys = new UserPasskey(db.dataSource());
            passkeys.save(1, "own-hash", "own-credential", "own-public-key", 0, "internal",
                    "Work computer", "aaguid", true, false, "https://example.com", "example.com", 1L);
            passkeys.save(2, "other-hash", "other-credential", "other-public-key", 0, "internal",
                    "Other account", "aaguid", true, false, "https://example.com", "example.com", 2L);

            com.google.gson.JsonObject data = gson.toJsonTree(service.serverSide(
                    page, request(page, "/blog", config), response()).getData()).getAsJsonObject();
            assertTrue(data.has("mfaEnabled"));
            assertEquals(1, data.getAsJsonArray("passkeys").size());
            com.google.gson.JsonObject summary = data.getAsJsonArray("passkeys").get(0).getAsJsonObject();
            assertEquals("Work computer", summary.get("name").getAsString());
            assertEquals(1L, summary.get("createdAt").getAsLong());
            assertFalse(data.toString().contains("Other account"));
            assertFalse(summary.has("credentialId"));
            assertFalse(summary.has("publicKeyCose"));

            AdminTokenThreadLocal.remove();
            assertNull(service.serverSide(page, request(page, "/blog", config), response()).getData());
        }
    }

    @Test
    public void shouldLoadNestedAccountPagesThroughExistingApisWithoutWeakeningPermissions() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            AccountAuthorizationTest.login(db, 1, "owner");
            db.loadAdminWebModules();
            Router router = db.config().getServerConfig().getRouter();
            RequestConfig config = new RequestConfig();
            config.setRouter(router);
            AdminPageService service = db.component(AdminUiComponent.class).pages();
            for (String page : AdminAccountPages.PAGE_APIS.keySet()) {
                if (page.equals(AdminAccountPages.AUTHORIZE)) continue;
                ServerSideDataResponse<Object> data = service.serverSide(page, request(page, "/blog", config), response());
                assertNotNull(page, data.getData());
                assertEquals(
                        AdminConstants.getAdminDocumentTitleByUri(
                                page, ApplicationServicesFixture.site(db.config()).get()),
                        data.getDocumentTitle());
            }
            assertTrue(
                    service.serverSide(
                                            "/admin/user/preferences/appearance",
                                            request(
                                                    "/admin/user/preferences/appearance",
                                                    "/blog",
                                                    config),
                                            response())
                                    .getData()
                            instanceof UserPreferencesResponse);
            assertTrue(
                    service.serverSide(
                                            "/admin/user/applications/tokens",
                                            request(
                                                    "/admin/user/applications/tokens",
                                                    "/blog",
                                                    config),
                                            response())
                                    .getData()
                            instanceof OAuthModels.Page);
            assertTrue(
                    service.serverSide(
                                            "/admin/website/members",
                                            request("/admin/website/members", "/blog", config),
                                            response())
                                    .getData()
                            instanceof MemberModels.Page);
            assertNotNull(router.getMethod("/api/admin/oauth/createPersonalToken", HttpMethod.POST));
            assertEquals(
                    AccountAction.PERMISSION_READ,
                    router.getMethod("/api/admin/access", HttpMethod.GET)
                            .getAnnotation(RequiresAction.class)
                            .value());
            assertFalse(router.getRouterMap().containsKey("/api/admin/user/applications"));
            for (String oldPage : java.util.List.of("/admin/user/preferences", "/admin/user/applications", "/admin/user/permissions", "/admin/user/members", "/admin/members", "/admin/access", "/admin/oauth", "/admin/oauth/authorize")) {
                assertFalse(oldPage, router.getRouterMap().containsKey(oldPage));
            }
            AccountAuthorizationTest.login(db, 1, "author");
            assertNotNull(service.serverSide("/admin/user/applications/tokens", request("/admin/user/applications/tokens", "/blog", config), response()).getData());
            org.junit.Assert.assertThrows(
                    PermissionErrorException.class,
                    () ->
                            service.serverSide(
                                    "/admin/website/members",
                                    request("/admin/website/members", "/blog", config),
                                    response()));
            assertNotNull(service.serverSide("/admin/user/preferences/session", request("/admin/user/preferences/session", "/blog", config), response()).getData());
            assertNotNull(service.serverSide("/admin/user/preferences/writing", request("/admin/user/preferences/writing", "/blog", config), response()).getData());
            assertNotNull(service.serverSide("/admin/user/preferences/assistant", request("/admin/user/preferences/assistant", "/blog", config), response()).getData());
            assertNotNull(service.serverSide("/admin/user/applications/grants", request("/admin/user/applications/grants", "/blog", config), response()).getData());
            org.junit.Assert.assertThrows(
                    PermissionErrorException.class,
                    () ->
                            service.serverSide(
                                    "/admin/user/applications/clients",
                                    request("/admin/user/applications/clients", "/blog", config),
                                    response()));
            org.junit.Assert.assertThrows(
                    PermissionErrorException.class,
                    () ->
                            service.serverSide(
                                    "/admin/user/applications/clients",
                                    request("/admin/ssJson", "/blog", config),
                                    response()));
            assertEquals(
                    AccountAction.OAUTH_CLIENT_MANAGE,
                    router.getMethod("/api/admin/oauth/clients", HttpMethod.GET)
                            .getAnnotation(RequiresAction.class)
                            .value());
            // ssJson uses the same page mapping and must enforce the same member action.
            org.junit.Assert.assertThrows(
                    PermissionErrorException.class,
                    () ->
                            service.serverSide(
                                    "/admin/website/members",
                                    request("/admin/ssJson", "/blog", config),
                                    response()));
        }
    }

    @Test
    public void shouldLoadProxyPageForAdministratorsAndRejectOtherRoles() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            NetworkProxy.save("http://proxy.example:3128", ".internal.example");
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            AdminPageService service = db.component(AdminUiComponent.class).pages();
            String page = "/admin/system/proxy";
            for (String contextPath : java.util.List.of("", "/blog")) {
                for (String uri : java.util.List.of(page, "/admin/ssJson")) {
                    HttpRequest request = request(uri, contextPath, config);
                    for (String role : java.util.List.of("owner", "admin")) {
                        AccountAuthorizationTest.login(db, 1, role);
                        ServerSideDataResponse<Object> response = service.serverSide(page, request, response());
                        ProxySettingsResponse settings = (ProxySettingsResponse) response.getData();
                        assertEquals("http://proxy.example:3128", settings.getUrl());
                        assertEquals(".internal.example", settings.getNoProxy());
                        assertEquals(
                                AdminConstants.getAdminDocumentTitleByUri(
                                        page, ApplicationServicesFixture.site(db.config()).get()),
                                response.getDocumentTitle());
                    }
                    for (String role : java.util.List.of("editor", "author", "contributor")) {
                        AccountAuthorizationTest.login(db, 1, role);
                        org.junit.Assert.assertThrows(
                                PermissionErrorException.class,
                                () -> service.serverSide(page, request, response()));
                    }
                }
            }
        }
    }

    @Test
    public void shouldLoadCompleteSystemInformationWithMatchingPermissionsAndTitle() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            AdminPageService service = db.component(AdminUiComponent.class).pages();
            for (String page : java.util.List.of("/admin/system")) {
                assertNotNull(config.getRouter().getMethod(page, HttpMethod.GET));
                Method api = config.getRouter().getMethod("/api" + page, HttpMethod.GET);
                assertNotNull(api);
                assertEquals(
                        AccountAction.SYSTEM_MANAGE,
                        api.getAnnotation(RequiresAction.class).value());
                for (String contextPath : java.util.List.of("", "/blog")) {
                    for (String uri : java.util.List.of(page, "/admin/ssJson")) {
                        HttpRequest request = request(uri, contextPath, config);
                        for (String role : java.util.List.of("owner", "admin")) {
                            AccountAuthorizationTest.login(db, 1, role);
                            ServerSideDataResponse<Object> result = service.serverSide(page, request, response());
                            SystemResponse data = (SystemResponse) result.getData();
                            assertFalse(data.getServerInfos().isEmpty());
                            assertFalse(data.getServerInfos2().isEmpty());
                            assertEquals(
                                    AdminConstants.getAdminDocumentTitleByUri(
                                            page,
                                            ApplicationServicesFixture.site(db.config()).get()),
                                    result.getDocumentTitle());
                        }
                        for (String role : java.util.List.of("editor", "author", "contributor")) {
                            AccountAuthorizationTest.login(db, 1, role);
                            org.junit.Assert.assertThrows(
                                    PermissionErrorException.class,
                                    () -> service.serverSide(page, request, response()));
                        }
                    }
                }
            }
        }
    }

    @Test(timeout = 30000)
    public void shouldLoadCanonicalVersionPageWithExistingPermissionsAndTitle() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            // Replace the network boundary before production database-ready callbacks build bindings.
            db.loadAdminWebModules(server -> {
                assertEquals(server.getRouter().getMethod("/api/admin/website/version", HttpMethod.GET),
                        server.getRouter().getMethod("/api/admin/system/version", HttpMethod.GET));
                server.getBindings().controller(OfflineVersionController.class,
                        (r, request, response) -> new OfflineVersionController(r.get(WebSiteSettingsService.class),
                                r.get(UpgradeSettingsService.class), r.get(FeatureLabService.class),
                                r.get(AdminPageDataFactory.class), db.dataSource()));
                server.getRouter().addMapper("/api/admin/system/version", OfflineVersionController.class, "version");
            });
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            String page = "/admin/system/version";
            assertNotNull(config.getRouter().getMethod(page, HttpMethod.GET));
            assertEquals(
                    AccountAction.SITE_CONFIGURE,
                    config.getRouter()
                            .getMethod("/api" + page, HttpMethod.GET)
                            .getAnnotation(RequiresAction.class)
                            .value());
            AdminPageService service = db.component(AdminUiComponent.class).pages();
            for (String contextPath : java.util.List.of("", "/blog")) {
                for (String uri : java.util.List.of(page, "/admin/ssJson")) {
                    HttpRequest request = request(uri, contextPath, config);
                    for (String role : java.util.List.of("owner", "admin")) {
                        AccountAuthorizationTest.login(db, 1, role);
                        ServerSideDataResponse<Object> result = service.serverSide(page, request, response());
                        assertNotNull(((VersionResponse) result.getData()).getVersion());
                        assertEquals("test changelog", ((VersionResponse) result.getData()).getChangelog());
                        assertEquals(
                                AdminConstants.getAdminDocumentTitleByUri(
                                        page, ApplicationServicesFixture.site(db.config()).get()),
                                result.getDocumentTitle());
                    }
                    for (String role : java.util.List.of("editor", "author", "contributor")) {
                        AccountAuthorizationTest.login(db, 1, role);
                        org.junit.Assert.assertThrows(
                                PermissionErrorException.class,
                                () -> service.serverSide(page, request, response()));
                    }
                }
            }
        }
    }

    @Test
    public void shouldRedirectExistingSystemSettingsPagesPreservingContextAndEncodedQuery() throws Throwable {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            RequestConfig config = new RequestConfig();
            config.setRouter(db.config().getServerConfig().getRouter());
            for (String page : java.util.List.of("version", "upgrade")) {
                Method route = config.getRouter().getMethod("/admin/website/" + page, HttpMethod.GET);
                for (String contextPath : java.util.List.of("", "/", "/blog")) {
                    for (String query : java.util.List.of("", "v=1&message=%E4%B8%AD%E6%96%87&value=a%2Bb%2525")) {
                        HttpRequest base = request("/admin/website/" + page, contextPath, config);
                        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class[]{HttpRequest.class}, (proxy, method, args) ->
                                        method.getName().equals("getQueryStr") ? query : method.invoke(base, args));
                        var response =
                                new SimpleHttpResponse(request, new ResponseConfig()) {
                                    @Override
                                    protected void send(
                                            byte[] bytes, boolean body, boolean close) {}
                                };
                        route.invoke(Controller.buildController(route, request, response));
                        String prefix = contextPath.equals("/") ? "" : contextPath;
                        assertEquals(prefix + "/admin/system/" + page + (query.isEmpty() ? "" : "?" + query),
                                response.getHeader().get("Location"));
                    }
                }
            }
        }
    }

    @Test
    public void shouldReuseUpgradeSettingsApiAndPermissionAtTheSystemPage() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            resources = db.resources();
            db.loadAdminWebModules();
            Router router = db.config().getServerConfig().getRouter();
            assertNotNull(router.getMethod("/admin/system/upgrade", HttpMethod.GET));
            for (HttpMethod method : java.util.List.of(HttpMethod.GET, HttpMethod.POST)) {
                Method route = router.getMethod("/api/admin/system/upgrade", method);
                assertNotNull(route);
                assertEquals(router.getMethod("/api/admin/website/upgrade", method), route);
                assertEquals(AccountAction.SITE_CONFIGURE, route.getAnnotation(RequiresAction.class).value());
            }
            assertEquals(
                    AdminConstants.getAdminDocumentTitleByUri(
                            "/admin/website/upgrade",
                            ApplicationServicesFixture.site(db.config()).get()),
                    AdminConstants.getAdminDocumentTitleByUri(
                            "/admin/system/upgrade",
                            ApplicationServicesFixture.site(db.config()).get()));
        }
    }

    @Test
    public void shouldReturnNullDataForNonPageServerSideResponse() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            resources = ignored.resources();
            setAdminToken();
            RequestConfig requestConfig = requestConfig(ignored.dataSource());

            ServerSideDataResponse<Object> response = pageService(ignored.unitCacheService(), ignored.dataSource()).serverSide(
                    "/admin/ssr/plain", request("/admin/ssr/plain", "/blog", requestConfig), response());

            assertNull(response.getData());
            assertEquals("session-1", response.getKey());
        }
    }

    @Test
    public void shouldPropagateServerSideControllerTargetException() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            resources = ignored.resources();
            setAdminToken();
            RequestConfig requestConfig = requestConfig(ignored.dataSource());

            IllegalStateException exception = org.junit.Assert.assertThrows(IllegalStateException.class,
                    () -> pageService(ignored.unitCacheService(), ignored.dataSource()).serverSide(
                            "/admin/ssr/explode", request("/admin/ssr/explode", "/blog", requestConfig), response()));

            assertEquals("boom", exception.getMessage());
        }
    }

    private AdminPageService pageService(CacheService cache, DataSourceWrapper testDataSource) {
        AdminResource ownedResources = resources;
        return new AdminPageService(
                cache,
                new AdminPageDataFactory(
                        ApplicationServicesFixture.site(applicationConfig),
                        ownedResources::getStaticResourceBuildId,
                        ApplicationServicesFixture.messages(testDataSource),
                        AccountServicesFixture.component(testDataSource).permissions()),
                resourceService(testDataSource),
                ownedResources::getStaticResourceBuildId,
                AccountServicesFixture.component(testDataSource).permissions(),
                AccountServicesFixture.component(testDataSource).preferences(),
                AccountServicesFixture.component(testDataSource).users());
    }

    private AdminResourceService resourceService(DataSourceWrapper testDataSource) {
        var site = ApplicationServicesFixture.site(applicationConfig);
        if (applicationConfig != null) {
            return new AdminResourceService(
                    AdminResourceInfoResponse.Capabilities::new,
                    new FeatureLabService(
                            () -> true,
                            new WebhookService(
                                    ApplicationServicesFixture.messages(testDataSource),
                                    ApplicationServicesFixture.websiteCache(testDataSource)),
                            () -> new WebSite(testDataSource)),
                    site,
                    () -> new WebSite(testDataSource).getStringValueByName("admin_static_resource_base_url"),
                    () -> new UserPasskey(testDataSource));
        }
        return new AdminResourceService(
                AdminResourceInfoResponse.Capabilities::new,
                new FeatureLabService(
                        () -> true,
                        new WebhookService(
                                ApplicationServicesFixture.messages(testDataSource),
                                ApplicationServicesFixture.websiteCache(testDataSource)),
                        () -> new WebSite(testDataSource)),
                site,
                () -> "",
                () -> new UserPasskey(testDataSource)) {
            @Override
            public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
                AdminResourceInfoResponse response = new AdminResourceInfoResponse();
                response.setCurrentVersion("3.6.0");
                response.setBuildId("build-1");
                return response;
            }
        };
    }

    private static InputStream html(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest request(String uri, String contextPath) {
        return request(uri, contextPath, null);
    }

    private HttpRequest request(String uri, String contextPath, RequestConfig requestConfig) {
        return (HttpRequest)
                Proxy.newProxyInstance(
                        AdminPageServiceTest.class.getClassLoader(),
                        new Class[] {HttpRequest.class},
                        (proxy, method, args) -> {
                            if ("getUri".equals(method.getName())) {
                                return uri;
                            }
                            if ("getContextPath".equals(method.getName())) {
                                return contextPath;
                            }
                            if ("getMethod".equals(method.getName())) {
                                return HttpMethod.GET;
                            }
                            if ("getHttpVersion".equals(method.getName())) {
                                return HttpVersion.HTTP_1_1;
                            }
                            if ("getHeader".equals(method.getName())) {
                                return null;
                            }
                            if ("getHeaderMap".equals(method.getName())) {
                                return Map.of();
                            }
                            if ("getRequestConfig".equals(method.getName())) {
                                return requestConfig;
                            }
                            if ("getServerConfig".equals(method.getName())) {
                                return applicationConfig.getServerConfig();
                            }
                            if ("toString".equals(method.getName())) {
                                return "HttpRequestProxy";
                            }
                            return null;
                        });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                AdminPageServiceTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    private RequestConfig requestConfig(DataSourceWrapper testDataSource) {
        applicationConfig
                .getServerConfig()
                .getBindings()
                .instance(
                        AdminPageDataFactory.class,
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                ApplicationServicesFixture.buildId(resources),
                                ApplicationServicesFixture.messages(testDataSource),
                                AccountServicesFixture.component(testDataSource).permissions()))
                .controller(
                        FakePageController.class,
                        (r, request, response) ->
                                new FakePageController(r.get(AdminPageDataFactory.class)));
        Router router = new Router();
        router.addMapper("/api/admin/ssr", FakePageController.class);
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setRouter(router);
        return requestConfig;
    }

    private static void setAdminToken() throws Exception {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setSessionId("session-1");
        token.setProtocol("http");
        Method method = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        method.setAccessible(true);
        method.invoke(null, token);
    }

    public static class OfflineVersionController extends WebSiteController {
        public OfflineVersionController(WebSiteSettingsService settings, UpgradeSettingsService upgrades,
                                        FeatureLabService features, AdminPageDataFactory pages, DataSourceWrapper testDataSource) {
            super(settings, upgrades, features, pages, ApplicationServicesFixture.settings(testDataSource));
        }


        @Override
        @ResponseBody
        @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "website.version")
        public AdminPageDataResponse<VersionResponse> version() {
            return super.version();
        }

        @Override
        protected String getCurrentChangeLog(Map<String, Object> backendMessages) {
            return "test changelog";
        }
    }

    public static class FakePageController extends Controller {
        private final AdminPageDataFactory pages;

        public FakePageController(AdminPageDataFactory pages) {
            this.pages = pages;
        }

        @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.pageData")
        @ResponseBody
        public AdminPageDataResponse<String> page() {
            return pages.response("page-data", "", request.getUri());
        }

        @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.pageData")
        @ResponseBody
        public ApiStandardResponse<Void> plain() {
            return new ApiStandardResponse<>();
        }

        @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.pageData")
        @ResponseBody
        public ApiStandardResponse<Void> empty() {
            return null;
        }

        @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.pageData")
        @ResponseBody
        public ApiStandardResponse<Void> explode() {
            throw new IllegalStateException("boom");
        }
    }

    private static class FakeAdminResource implements AdminResource {
        @Override
        public Set<String> getAdminStaticResourceUris() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getAdminPageUris() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getAdminStaticCacheUris() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getAdminCacheableApiUris() {
            return Collections.emptySet();
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return null;
        }

        @Override
        public String getStaticResourceBuildId() {
            return "build-1";
        }

    }
}
