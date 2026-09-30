package com.zrlog.admin.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BackendServerUrlTest {
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
