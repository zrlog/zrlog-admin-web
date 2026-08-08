package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.UserPasskey;
import com.zrlog.plugin.BaseStaticSitePlugin;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AdminResourceImplTest {

    @Test
    public void shouldExposeAdminResourceUrisAndBuildServiceWorkerUrls() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("admin_static_resource_base_url", "https://cdn.example.com");
            AdminResourceImpl resource = new AdminResourceImpl("/blog");

            Set<String> pageUris = resource.getAdminPageUris();
            Set<String> cacheUris = resource.getAdminStaticCacheUris();
            Set<String> apiUris = resource.getAdminCacheableApiUris();
            String normalServiceWorker = read(resource.renderServiceWorker(request("Browser")));
            String staticServiceWorker = read(resource.renderServiceWorker(request(BaseStaticSitePlugin.STATIC_USER_AGENT)));

            assertFalse(resource.getAdminStaticResourceUris().isEmpty());
            assertTrue(pageUris.stream().allMatch(uri -> uri.startsWith("/blog/admin/")));
            assertTrue(pageUris.contains("/blog/admin/index"));
            assertTrue(cacheUris.contains("/blog" + AdminConstants.ADMIN_PWA_MANIFEST_JSON));
            assertTrue(cacheUris.contains("/blog" + AdminConstants.ADMIN_SERVICE_WORKER_JS));
            assertTrue(apiUris.contains("/api/admin/website"));
            assertTrue(normalServiceWorker.contains("const urlsToCache = ["));
            assertTrue(normalServiceWorker.contains("https://cdn.example.com/blog/admin/index?"));
            assertTrue(staticServiceWorker.contains("/blog/admin/index.html?"));
            assertNotEquals("", resource.getStaticResourceBuildId());
        }
    }

    @Test
    public void shouldBuildAdminResourceInfoFromRealWebsiteAndFeatureTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("feature_webhook_enabled", true);
            db.putWebsite("feature_personal_data_enabled", true);
            db.putWebsite("admin_static_resource_base_url", "https://cdn.example.com");
            AdminResourceImpl resource = new AdminResourceImpl("/blog");

            AdminResourceInfoResponse response = resource.adminResourceInfo(request("Browser"));
            AdminResourceInfoResponse staticResponse =
                    resource.adminResourceInfo(request(BaseStaticSitePlugin.STATIC_USER_AGENT));
            AdminResourceInfoResponse trustedCrossOriginResponse = resource.adminResourceInfo(
                    request("Browser", "https://localhost:18080", "faas.example.com"));
            AdminResourceInfoResponse untrustedCrossOriginResponse = resource.adminResourceInfo(
                    request("Browser", "https://evil.example.com", "faas.example.com"));

            assertEquals("ZrLog Test", response.getWebsiteTitle());
            assertEquals(false, response.getAdmin_darkMode());
            assertEquals("default", response.getAdmin_theme());
            assertEquals(false, response.getAdmin_compactMode());
            assertEquals(WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR, response.getAdmin_color_primary());
            assertEquals("//localhost:18080/blog/", response.getHomeUrl());
            assertEquals("", response.getArticleRoute());
            assertEquals(false, response.getStaticPage());
            assertEquals(false, response.getStaticPlugin());
            assertEquals(true, response.getSupportSse());
            assertEquals("https://cdn.example.com/blog", response.getAdmin_static_resource_base_url());
            assertEquals(true, response.getFeature_webhook_enabled());
            assertEquals(true, response.getFeature_personal_data_enabled());
            assertEquals(false, response.getPasskeyLoginEnabled());
            assertEquals(true, staticResponse.getStaticPage());
            assertEquals("/blog", staticResponse.getAdmin_static_resource_base_url());
            assertEquals(false, trustedCrossOriginResponse.getPasskeyLoginEnabled());
            assertEquals(false, untrustedCrossOriginResponse.getPasskeyLoginEnabled());

            UserPasskey passkeys = new UserPasskey();
            assertTrue(passkeys.save(1, "other-credential-hash", "other-credential-id", "public-key",
                    0, "internal", "Other origin passkey", "aaguid", true, false,
                    "https://localhost:18080", "localhost", System.currentTimeMillis()));
            assertEquals(true, resource.adminResourceInfo(request("Browser")).getPasskeyLoginEnabled());
            assertEquals(true, resource.adminResourceInfo(request(BaseStaticSitePlugin.STATIC_USER_AGENT))
                    .getPasskeyLoginEnabled());
            assertEquals(true, resource.adminResourceInfo(
                    request("Browser", "https://localhost:18080", "faas.example.com"))
                    .getPasskeyLoginEnabled());
            assertEquals(true, resource.adminResourceInfo(
                    request("Browser", "https://evil.example.com", "faas.example.com"))
                    .getPasskeyLoginEnabled());
            assertTrue(passkeys.save(1, "credential-hash", "credential-id", "public-key",
                    0, "internal", "Test passkey", "aaguid", true, false,
                    "https://request.example.com", "request.example.com", System.currentTimeMillis()));

            long otherPasskeyId = ((Number) passkeys.findByCredentialIdHash("other-credential-hash").get("id"))
                    .longValue();
            assertTrue(passkeys.deleteByIdAndUserId(otherPasskeyId, 1));
            assertEquals(true, resource.adminResourceInfo(request("Browser")).getPasskeyLoginEnabled());

            long passkeyId = ((Number) passkeys.findByCredentialIdHash("credential-hash").get("id")).longValue();
            assertTrue(passkeys.deleteByIdAndUserId(passkeyId, 1));
            assertEquals(false, resource.adminResourceInfo(request("Browser")).getPasskeyLoginEnabled());
            assertEquals(false, resource.adminResourceInfo(request(BaseStaticSitePlugin.STATIC_USER_AGENT))
                    .getPasskeyLoginEnabled());
        }
    }

    private static String read(InputStream inputStream) throws Exception {
        try (InputStream input = inputStream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HttpRequest request(String userAgent) {
        return request(userAgent, null, "request.example.com");
    }

    private static HttpRequest request(String userAgent, String origin, String host) {
        Map<String, Object> attrs = new HashMap<>();
        return (HttpRequest) Proxy.newProxyInstance(
                AdminResourceImplTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContextPath":
                            return "/blog";
                        case "getUri":
                            return "/admin/index";
                        case "getQueryStr":
                            return "";
                        case "getHeader":
                            if ("User-Agent".equals(args[0])) {
                                return userAgent;
                            }
                            if ("Host".equals(args[0])) {
                                return host;
                            }
                            if ("Origin".equals(args[0])) {
                                return origin;
                            }
                            return null;
                        case "getHeaderMap":
                            Map<String, String> headers = new HashMap<>();
                            headers.put("User-Agent", userAgent);
                            headers.put("Host", host);
                            if (origin != null) {
                                headers.put("Origin", origin);
                            }
                            return headers;
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getScheme":
                            return "https";
                        case "getAttr":
                            return attrs;
                        case "toString":
                            return "AdminResourceRequest";
                        default:
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }
}
