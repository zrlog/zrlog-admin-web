package com.zrlog.admin.business.service;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.Router;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.AdminTokenVO;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdminPageServiceTest {

    private AdminResource previousResource;

    @Before
    public void setUp() {
        previousResource = AdminConstants.adminResource;
        AdminConstants.adminResource = new FakeAdminResource();
        AdminTokenThreadLocal.remove();
    }

    @After
    public void tearDown() {
        AdminConstants.adminResource = previousResource;
        AdminTokenThreadLocal.remove();
    }

    @Test
    public void shouldBuildServerSideDataForAnonymousRequest() throws Throwable {
        ServerSideDataResponse<Object> response = new AdminPageService().serverSide(
                "/admin/index", request("/admin/index", "/blog"), response());

        assertNull(response.getUser());
        assertNull(response.getData());
        assertNull(response.getKey());
        assertNotNull(response.getResourceInfo());
        assertEquals("3.6.0", response.getResourceInfo().getCurrentVersion());
    }

    @Test
    public void shouldRewriteAdminHtmlForNormalPage() throws Throwable {
        String html = new AdminPageService().buildHtml(
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
        assertEquals("build-1", AdminConstants.adminResource.getStaticResourceBuildId());
        assertTrue(document.getElementById("__SS_DATA__").text().contains("3.6.0"));
    }

    @Test
    public void shouldRemoveManifestOnLoginPage() throws Throwable {
        String html = new AdminPageService().buildHtml(
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
            db.putWebsite("admin_static_resource_base_url", "https://cdn.example");

            String html = new AdminPageService().buildHtml(
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
            setAdminToken();
            RequestConfig requestConfig = requestConfig();

            ServerSideDataResponse<Object> response = new AdminPageService().serverSide(
                    "/admin/ssr/page", request("/admin/ssr/page", "/blog", requestConfig), response());

            assertEquals("admin", response.getUser().getUserName());
            assertEquals("session-1", response.getKey());
            assertEquals("page-data", response.getData());
            assertNotNull(response.getDocumentTitle());
        }
    }

    @Test
    public void shouldUseEmptyObjectWhenServerSideControllerReturnsNull() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            RequestConfig requestConfig = requestConfig();

            ServerSideDataResponse<Object> response = new AdminPageService().serverSide(
                    "/admin/ssr/empty", request("/admin/ssr/empty", "/blog", requestConfig), response());

            assertNotNull(response.getData());
            assertEquals(Object.class, response.getData().getClass());
        }
    }

    @Test
    public void shouldReturnNullDataForNonPageServerSideResponse() throws Throwable {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            RequestConfig requestConfig = requestConfig();

            ServerSideDataResponse<Object> response = new AdminPageService().serverSide(
                    "/admin/ssr/plain", request("/admin/ssr/plain", "/blog", requestConfig), response());

            assertNull(response.getData());
            assertEquals("session-1", response.getKey());
        }
    }

    @Test
    public void shouldPropagateServerSideControllerTargetException() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            setAdminToken();
            RequestConfig requestConfig = requestConfig();

            IllegalStateException exception = org.junit.Assert.assertThrows(IllegalStateException.class,
                    () -> new AdminPageService().serverSide(
                            "/admin/ssr/explode", request("/admin/ssr/explode", "/blog", requestConfig), response()));

            assertEquals("boom", exception.getMessage());
        }
    }

    private static InputStream html(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpRequest request(String uri, String contextPath) {
        return request(uri, contextPath, null);
    }

    private static HttpRequest request(String uri, String contextPath, RequestConfig requestConfig) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminPageServiceTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
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
                    if ("getHeader".equals(method.getName())) {
                        return null;
                    }
                    if ("getHeaderMap".equals(method.getName())) {
                        return Map.of();
                    }
                    if ("getRequestConfig".equals(method.getName())) {
                        return requestConfig;
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

    private static RequestConfig requestConfig() {
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

    public static class FakePageController extends Controller {

        @ResponseBody
        public AdminPageDataResponse<String> page() {
            return new AdminPageDataResponse<>("page-data", "", request.getUri());
        }

        @ResponseBody
        public ApiStandardResponse<Void> plain() {
            return new ApiStandardResponse<>();
        }

        @ResponseBody
        public ApiStandardResponse<Void> empty() {
            return null;
        }

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

        @Override
        public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
            AdminResourceInfoResponse response = new AdminResourceInfoResponse();
            response.setCurrentVersion("3.6.0");
            response.setBuildId(getStaticResourceBuildId());
            return response;
        }
    }
}
