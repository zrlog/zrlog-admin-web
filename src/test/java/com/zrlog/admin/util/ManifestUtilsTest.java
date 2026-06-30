package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.response.AdminManifestResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ManifestUtilsTest {

    @Test
    public void shouldLoadManifestIconsFromBundledManifest() throws Exception {
        List<AdminManifestResponse.Icon> icons = ManifestUtils.getManifestIcons();

        assertFalse(icons.isEmpty());
        for (AdminManifestResponse.Icon icon : icons) {
            assertNotNull(icon.getSrc());
            assertTrue(icon.getSrc().startsWith("/admin/"));
            assertTrue(icon.getSrc().contains("?v="));
        }
    }

    @Test
    public void shouldBuildManifestFromPublicWebsiteInfo() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new TestZrLogConfig(publicWebSiteInfo(false));

            AdminManifestResponse manifest = ManifestUtils.manifest(request(null));

            assertEquals("Admin Test Blog", manifest.getShort_name());
            assertTrue(manifest.getName().contains("Admin Test Blog"));
            assertEquals("Admin description", manifest.getDescription());
            assertEquals("admin-app-id", manifest.getId());
            assertEquals("#45a29e", manifest.getTheme_color());
            assertEquals("#FFFFFF", manifest.getBackground_color());
            assertFalse(manifest.getIcons().isEmpty());
            assertTrue(manifest.getIcons().get(0).getSrc().startsWith("/blog/admin/"));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldRewriteManifestStartUrlForStaticPluginRequests() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new TestZrLogConfig(publicWebSiteInfo(true));

            AdminManifestResponse manifest = ManifestUtils.manifest(request(BaseStaticSitePlugin.STATIC_USER_AGENT));

            assertEquals("#000000", manifest.getBackground_color());
            assertEquals("./index.html?pwa=true", manifest.getStart_url());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    private static PublicWebSiteInfo publicWebSiteInfo(boolean darkMode) {
        PublicWebSiteInfo info = new PublicWebSiteInfo();
        info.setTitle("Admin Test Blog");
        info.setDescription("Admin description");
        info.setAppId("admin-app-id");
        info.setAdmin_color_primary("#45a29e");
        info.setAdmin_darkMode(darkMode);
        return info;
    }

    private static HttpRequest request(String userAgent) {
        return (HttpRequest) Proxy.newProxyInstance(
                ManifestUtilsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return "/blog";
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

    private static class TestZrLogConfig extends ZrLogConfig {

        TestZrLogConfig(PublicWebSiteInfo publicWebSiteInfo) {
            super(18080, null, "");
            this.cacheService = cacheService(publicWebSiteInfo);
        }

        @Override
        public boolean isInstalled() {
            return false;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }

        @Override
        public DataSource getDataSource() {
            return null;
        }
    }

    private static CacheService cacheService(PublicWebSiteInfo publicWebSiteInfo) {
        return (CacheService) Proxy.newProxyInstance(
                ManifestUtilsTest.class.getClassLoader(),
                new Class[]{CacheService.class},
                (proxy, method, args) -> {
                    if ("getPublicWebSiteInfo".equals(method.getName())) {
                        return publicWebSiteInfo;
                    }
                    if ("getCurrentSqlVersion".equals(method.getName())
                            || "getWebSiteVersion".equals(method.getName())) {
                        return 0L;
                    }
                    if ("toString".equals(method.getName())) {
                        return "CacheServiceProxy";
                    }
                    return null;
                });
    }
}
