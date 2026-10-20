package com.zrlog.admin.util;

import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.plugin.BaseStaticSitePlugin;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AdminWebToolsTest {


    private static HttpRequest request(String uri, String contextPath, String userAgent) {
        ServerConfig serverConfig = new ServerConfig();
        return (HttpRequest) Proxy.newProxyInstance(
                AdminWebToolsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("getHttpVersion".equals(method.getName())) {
                        return HttpVersion.HTTP_1_1;
                    }
                    if ("getHeader".equals(method.getName()) && "User-Agent".equals(args[0])) {
                        return userAgent;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                AdminWebToolsTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    @Test
    public void shouldRejectApiRequestWhenSiteIsMissingInstall() throws Exception {
        assertThrows(MissingInstallException.class,
                () -> AdminWebTools.blockUnLoginRequestHandler(request("/api/admin", "/blog", null), response(), false));
    }

    @Test
    public void shouldRejectApiRequestWhenAdminTokenIsMissing() throws Exception {
        assertThrows(AdminAuthException.class,
                () -> AdminWebTools.blockUnLoginRequestHandler(request("/api/admin", "/blog", null), response(), true));
    }

    @Test
    public void shouldFrameAdminPageRedirectAsEmptyResponse() {
        HttpRequest request = request("/admin", "/blog", null);
        HttpResponse response = new SimpleHttpResponse(request, new ResponseConfig());

        AdminWebTools.blockUnLoginRequestHandler(request, response, true);

        assertEquals("0", response.getHeader().get("Content-Length"));
    }

    @Test
    public void shouldUseContextPathForStaticPluginAdminAssets() throws Exception {
        String baseUrl = AdminWebTools.getAdminStaticResourceBaseUrl(
                request("/admin/assets/app.js", "/blog", BaseStaticSitePlugin.STATIC_USER_AGENT),
                () -> {
                    throw new AssertionError("Static export must use local assets");
                });

        assertEquals("/blog", baseUrl);
    }
}
