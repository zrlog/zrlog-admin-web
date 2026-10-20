package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BackendServerUrlTest {

    private static HttpRequest request(String origin, String scheme) {
        return (HttpRequest) Proxy.newProxyInstance(BackendServerUrlTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getScheme")) return scheme;
                    if (method.getName().equals("getHeader")) return "Origin".equals(args[0]) ? origin : "evil.example";
                    return null;
                });
    }

    @Test
    public void browserOriginsSupportHttpHostsAndContextPathsWithoutWeakeningOAuth() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var backend = ApplicationServicesFixture.backend(db.config());
            var server = db.config().getServerConfig();
            for (String host : List.of("192.168.1.12:8080", "blog.example", "blog.example:80", "[fd00::1]:8080")) {
                db.unitCacheService().getPublicWebSiteInfo().setHost(host);
                for (String context : List.of("", "/sub")) {
                    server.setContextPath(context);
                    backend.requireAdminRequestOrigin(request("http://" + host, "http"));
                    backend.requireAdminRequestOrigin(request("https://" + host, "https"));
                    assertThrows(IllegalArgumentException.class,
                            () -> backend.requireAdminRequestOrigin(request("https://" + host, "http")));
                }
            }
            db.unitCacheService().getPublicWebSiteInfo().setHost("blog.example");
            backend.requireAdminRequestOrigin(request("http://blog.example:80", "http"));
            assertThrows(IllegalArgumentException.class, () -> BackendServerUrl.normalize("http://blog.example"));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            backend.requireAdminOrigin(
                                    "http://blog.example", "http://blog.example/sub"));
        }
    }

    @Test
    public void browserOriginsUseExplicitBackendAndFrontendConfiguration() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var backend = ApplicationServicesFixture.backend(db.config());
            var server = db.config().getServerConfig();
            db.putWebsite(BackendServerUrl.SETTING_KEY, "http://api.internal:8080/proxy/sub");
            db.putWebsite("admin_static_resource_base_url", "http://admin.internal:3000/assets/sub");
            backend.requireAdminRequestOrigin(request("http://api.internal:8080", "http"));
            backend.requireAdminRequestOrigin(request("http://admin.internal:3000", "http"));
            assertThrows(IllegalArgumentException.class,
                    () -> backend.requireAdminRequestOrigin(request("http://localhost:18080", "http")));
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example/proxy/sub");
            // Explicit external HTTPS origins remain authoritative behind an HTTP reverse proxy.
            backend.requireAdminRequestOrigin(request("https://api.example", "http"));
            db.putWebsite("admin_static_resource_base_url", "");
            assertThrows(IllegalArgumentException.class,
                    () -> backend.requireAdminRequestOrigin(request("http://admin.internal:3000", "http")));
        }
    }

    @Test
    public void browserOriginsRejectUntrustedAndMalformedHttpHeaders() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var backend = ApplicationServicesFixture.backend(db.config());
            var server = db.config().getServerConfig();
            db.unitCacheService().getPublicWebSiteInfo().setHost("blog.example");
            for (String origin : Arrays.asList(null, "", "null", "http://evil.example", "http://blog.example.evil.example",
                    "http://blog.example:8080", "https://blog.example", "http://blog.example:0", "http://blog.example:65536",
                    "http://blog.example/sub", "http://blog.example?x=1", "http://blog.example#x",
                    "http://user@blog.example", "//blog.example", "ftp://blog.example",
                    "http://blog.example/../", "http://blog.example/%2e%2e", "http://blog.example/%2f",
                    "http://blog.example http://evil.example")) {
                assertThrows(String.valueOf(origin), IllegalArgumentException.class,
                        () -> backend.requireAdminRequestOrigin(request(origin, "http")));
            }
            for (String frontend : List.of("/admin", "//admin.example", "http://user@admin.example",
                    "http://admin.example?x=1", "http://admin.example#fragment", "http://admin.example:65536",
                    "http://admin.example/../sub", "http://admin.example/%2e%2e/sub", "http://admin.example/sub%2fpath")) {
                db.putWebsite("admin_static_resource_base_url", frontend);
                assertThrows(frontend, IllegalArgumentException.class,
                        () -> backend.requireAdminRequestOrigin(request("http://admin.example", "http")));
                backend.requireAdminRequestOrigin(request("http://blog.example", "http"));
            }
        }
    }

    @Test
    public void explicitAddressesRemainAuthoritativeForLocalInstallations() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var backend = ApplicationServicesFixture.backend(db.config());
            var server = db.config().getServerConfig();
            server.setContextPath("/sub");
            db.unitCacheService().getPublicWebSiteInfo().setHost("blog.example");
            assertEquals("https://blog.example/sub", backend.configured());
            db.unitCacheService().getPublicWebSiteInfo().setHost("");
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example/proxy/sub/");
            assertEquals("https://api.example/proxy/sub", backend.configured());
            for (String invalid : List.of("/sub", "http://api.example", "https://user@api.example",
                    "https://api.example?x=1", "https://api.example/../sub")) {
                db.putWebsite(BackendServerUrl.SETTING_KEY, invalid);
                assertThrows(invalid, IllegalArgumentException.class, backend::configured);
            }
            db.putWebsite(BackendServerUrl.SETTING_KEY, "");
            assertEquals("http://localhost:18080/sub", backend.configured());
        }
    }

    @Test
    public void localFallbackRequiresAValidListenerPort() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            var backend = ApplicationServicesFixture.backend(db.config());
            var server = db.config().getServerConfig();
            db.unitCacheService().getPublicWebSiteInfo().setHost("");
            for (Integer port : Arrays.asList(null, -1, 0, 65536)) {
                server.setPort(port);
                assertThrows(IllegalArgumentException.class, backend::configured);
            }
            db.putWebsite(BackendServerUrl.SETTING_KEY, "https://api.example");
            assertEquals("https://api.example", backend.configured());
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
