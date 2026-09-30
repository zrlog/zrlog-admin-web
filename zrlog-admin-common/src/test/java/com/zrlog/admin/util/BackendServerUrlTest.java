package com.zrlog.admin.util;

import org.junit.Test;

import java.util.List;
import java.util.Arrays;
import java.lang.reflect.Proxy;
import com.hibegin.http.server.api.HttpRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BackendServerUrlTest {
    @Test
    public void browserOriginsSupportHttpHostsAndContextPathsWithoutWeakeningOAuth() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            for (String host : List.of("192.168.1.12:8080", "blog.example", "blog.example:80", "[fd00::1]:8080")) {
                db.cacheService().getPublicWebSiteInfo().setHost(host);
                for (String context : List.of("", "/sub")) {
                    com.zrlog.common.Constants.zrLogConfig.getServerConfig().setContextPath(context);
                    BackendServerUrl.requireAdminRequestOrigin(request("http://" + host, "http"));
                    BackendServerUrl.requireAdminRequestOrigin(request("https://" + host, "https"));
                    assertThrows(IllegalArgumentException.class,
                            () -> BackendServerUrl.requireAdminRequestOrigin(request("https://" + host, "http")));
                }
            }
            db.cacheService().getPublicWebSiteInfo().setHost("blog.example");
            BackendServerUrl.requireAdminRequestOrigin(request("http://blog.example:80", "http"));
            assertThrows(IllegalArgumentException.class, () -> BackendServerUrl.normalize("http://blog.example"));
            assertThrows(IllegalArgumentException.class,
                    () -> BackendServerUrl.requireAdminOrigin("http://blog.example", "http://blog.example/sub"));
        }
    }

    @Test
    public void browserOriginsUseExplicitBackendAndFrontendConfiguration() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            db.putWebsite(BackendServerUrl.SETTING_KEY, "http://api.internal:8080/proxy/sub");
            db.putWebsite("admin_static_resource_base_url", "http://admin.internal:3000/assets/sub");
            BackendServerUrl.requireAdminRequestOrigin(request("http://api.internal:8080", "http"));
            BackendServerUrl.requireAdminRequestOrigin(request("http://admin.internal:3000", "http"));
            assertThrows(IllegalArgumentException.class,
                    () -> BackendServerUrl.requireAdminRequestOrigin(request("http://localhost:18080", "http")));
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example/proxy/sub");
            // Explicit external HTTPS origins remain authoritative behind an HTTP reverse proxy.
            BackendServerUrl.requireAdminRequestOrigin(request("https://api.example", "http"));
            db.putWebsite("admin_static_resource_base_url", "");
            assertThrows(IllegalArgumentException.class,
                    () -> BackendServerUrl.requireAdminRequestOrigin(request("http://admin.internal:3000", "http")));
        }
    }

    @Test
    public void browserOriginsRejectUntrustedAndMalformedHttpHeaders() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            db.cacheService().getPublicWebSiteInfo().setHost("blog.example");
            for (String origin : Arrays.asList(null, "", "null", "http://evil.example", "http://blog.example.evil.example",
                    "http://blog.example:8080", "https://blog.example", "http://blog.example:0", "http://blog.example:65536",
                    "http://blog.example/sub", "http://blog.example?x=1", "http://blog.example#x",
                    "http://user@blog.example", "//blog.example", "ftp://blog.example",
                    "http://blog.example/../", "http://blog.example/%2e%2e", "http://blog.example/%2f",
                    "http://blog.example http://evil.example")) {
                assertThrows(String.valueOf(origin), IllegalArgumentException.class,
                        () -> BackendServerUrl.requireAdminRequestOrigin(request(origin, "http")));
            }
            for (String frontend : List.of("/admin", "//admin.example", "http://user@admin.example",
                    "http://admin.example?x=1", "http://admin.example#fragment", "http://admin.example:65536",
                    "http://admin.example/../sub", "http://admin.example/%2e%2e/sub", "http://admin.example/sub%2fpath")) {
                db.putWebsite("admin_static_resource_base_url", frontend);
                assertThrows(frontend, IllegalArgumentException.class,
                        () -> BackendServerUrl.requireAdminRequestOrigin(request("http://admin.example", "http")));
                BackendServerUrl.requireAdminRequestOrigin(request("http://blog.example", "http"));
            }
        }
    }

    private static HttpRequest request(String origin, String scheme) {
        return (HttpRequest) Proxy.newProxyInstance(BackendServerUrlTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getScheme")) return scheme;
                    if (method.getName().equals("getHeader")) return "Origin".equals(args[0]) ? origin : "evil.example";
                    return null;
                });
    }

    @Test
    public void explicitAddressesRemainAuthoritativeForLocalInstallations() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            var server = com.zrlog.common.Constants.zrLogConfig.getServerConfig();
            server.setContextPath("/sub");
            db.cacheService().getPublicWebSiteInfo().setHost("blog.example");
            assertEquals("https://blog.example/sub", BackendServerUrl.configured());
            db.cacheService().getPublicWebSiteInfo().setHost("");
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example/proxy/sub/");
            assertEquals("https://api.example/proxy/sub", BackendServerUrl.configured());
            for (String invalid : List.of("/sub", "http://api.example", "https://user@api.example",
                    "https://api.example?x=1", "https://api.example/../sub")) {
                db.putWebsite(BackendServerUrl.SETTING_KEY, invalid);
                assertThrows(invalid, IllegalArgumentException.class, BackendServerUrl::configured);
            }
            db.putWebsite(BackendServerUrl.SETTING_KEY, "");
            assertEquals("http://localhost:18080/sub", BackendServerUrl.configured());
        }
    }

    @Test
    public void localFallbackRequiresAValidListenerPort() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            db.cacheService().getPublicWebSiteInfo().setHost("");
            for (Integer port : java.util.Arrays.asList(null, -1, 0, 65536)) {
                com.zrlog.common.Constants.zrLogConfig.getServerConfig().setPort(port);
                assertThrows(IllegalArgumentException.class, BackendServerUrl::configured);
            }
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example");
            assertEquals("https://api.example", BackendServerUrl.configured());
        }
    }

    @Test
    public void configuredBackendIncludesContextExactlyOnce() {
        assertEquals("https://api.example/sub", BackendServerUrl.resolve("https://api.example/", "blog.example", "/sub"));
        assertEquals("https://api.example/proxy/sub", BackendServerUrl.resolve("https://api.example/proxy/sub/", "blog.example", "/sub"));
        assertEquals("https://blog.example/sub", BackendServerUrl.resolve(null, "blog.example", "/sub"));
        assertEquals("http://localhost:18080/sub", BackendServerUrl.resolve("", "localhost:18080", "/sub"));
    }

    @Test
    public void malformedBackendAndContextCannotProduceUnsafeSourceLinks() {
        for (String address : List.of("/sub", "http://api.example", "https://user@api.example", "https://api.example?x=1",
                "https://api.example#fragment", "https://api.example/../sub", "https://api.example/%2e%2e/sub",
                "https://api.example/sub%2fpath", "https://api.example:65536")) {
            assertThrows(address, IllegalArgumentException.class, () -> BackendServerUrl.resolve(address, "blog.example", "/sub"));
        }
        for (String context : List.of("/../sub", "/sub?x=1", "/sub#fragment", "//sub")) {
            assertThrows(context, IllegalArgumentException.class, () -> BackendServerUrl.resolve("https://api.example", "blog.example", context));
        }
    }
}
