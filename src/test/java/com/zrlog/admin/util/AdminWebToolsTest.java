package com.zrlog.admin.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;
import com.zrlog.admin.business.exception.AdminAuthException;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AdminWebToolsTest {

    private final ZrLogConfig previousConfig = Constants.zrLogConfig;

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
    }

    @Test
    public void shouldRejectApiRequestWhenSiteIsMissingInstall() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(false);

        assertThrows(MissingInstallException.class,
                () -> AdminWebTools.blockUnLoginRequestHandler(request("/api/admin", "/blog", null), response()));
    }

    @Test
    public void shouldRejectApiRequestWhenAdminTokenIsMissing() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);

        assertThrows(AdminAuthException.class,
                () -> AdminWebTools.blockUnLoginRequestHandler(request("/api/admin", "/blog", null), response()));
    }

    @Test
    public void shouldFrameAdminPageRedirectAsEmptyResponse() {
        HttpRequest request = request("/admin", "/blog", null);
        HttpResponse response = new SimpleHttpResponse(request, new ResponseConfig());

        AdminWebTools.blockUnLoginRequestHandler(request, response);

        assertEquals("0", response.getHeader().get("Content-Length"));
    }

    @Test
    public void shouldUseContextPathForStaticPluginAdminAssets() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);

        String baseUrl = AdminWebTools.getAdminStaticResourceBaseUrlByWebSite(
                request("/admin/assets/app.js", "/blog", BaseStaticSitePlugin.STATIC_USER_AGENT));

        assertEquals("/blog", baseUrl);
    }

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

    private static class TestZrLogConfig extends ZrLogConfig {

        private final boolean installed;

        TestZrLogConfig(boolean installed) {
            super(19084, null, "/");
            this.installed = installed;
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }
}
