package com.zrlog.admin.business.service;

import com.zrlog.admin.business.security.OAuthException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class OAuthAdminOriginTest {
    private static final String SETTING = "admin_static_resource_base_url";
    private final OAuthService service = new OAuthService(() -> "https://api.example/sub");

    @Test public void onlyBackendOriginIsTrustedWithoutAConfiguredFrontend() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            service.requireAdminOrigin("https://api.example");
            service.requireAdminOrigin("https://api.example:443");
            assertDenied("https://admin.example");
            db.putWebsite(SETTING, " ");
            assertDenied("https://admin.example");
        }
    }

    @Test public void configuredFrontendPathsAndDefaultPortsDoNotChangeItsOrigin() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(SETTING, " HTTPS://Admin.Example:443/assets/sub/ ");
            service.requireAdminOrigin("https://admin.example");
            service.requireAdminOrigin("https://admin.example:443");
            service.requireAdminOrigin("https://api.example");
            assertEquals("https://api.example/sub", service.issuer());
            assertEquals("https://api.example/sub/mcp", service.mcpResource());
            assertEquals("https://api.example/sub/oauth/authorize", service.metadata().authorization_endpoint);
            // The MCP endpoint retains its separate, backend-only origin policy.
            assertThrows(OAuthException.class, () -> service.requireSameOrigin("https://admin.example"));
        }
    }

    @Test public void changedOrClearedConfigurationTakesEffectImmediately() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(SETTING, "https://admin.example");
            service.requireAdminOrigin("https://admin.example");
            db.putWebsite(SETTING, "https://next.example/admin");
            assertDenied("https://admin.example");
            service.requireAdminOrigin("https://next.example");
            db.putWebsite(SETTING, "");
            assertDenied("https://next.example");
            service.requireAdminOrigin("https://api.example");
        }
    }

    @Test public void rejectsOtherOriginsAndMalformedBrowserHeaders() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(SETTING, "https://admin.example/assets");
            for (String origin : Arrays.asList(null, "", "null", "https://evil.example",
                    "https://admin.example.evil.example", "https://child.admin.example", "http://admin.example",
                    "https://admin.example:8443", "https://admin.example:0", "https://admin.example:65536",
                    "https://admin.example/assets", "https://admin.example?x=1", "https://admin.example#fragment",
                    "https://user@admin.example", "https://admin.example@evil.example", "//admin.example",
                    "https://admin.example\\evil", "https://admin.example https://evil.example")) {
                assertThrows(String.valueOf(origin), OAuthException.class, () -> service.requireAdminOrigin(origin));
            }
        }
    }

    @Test public void malformedConfigurationNeverGrantsAdditionalTrust() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            for (String url : List.of("/admin", "//admin.example", "http://admin.example",
                    "https://user@admin.example", "https://admin.example?x=1", "https://admin.example#fragment",
                    "https://admin.example:0", "https://admin.example:65536", "https://admin.example/../assets",
                    "https://admin.example/%2e%2e/assets", "https://admin.example/sub%2fassets")) {
                db.putWebsite(SETTING, url);
                assertDenied("https://admin.example");
                service.requireAdminOrigin("https://api.example");
            }
        }
    }

    @Test public void localDevelopmentStillRequiresTheConfiguredHostAndPort() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(SETTING, "http://localhost:3000/admin");
            service.requireAdminOrigin("http://localhost:3000");
            assertDenied("http://localhost:3001");
            assertDenied("http://127.0.0.1:3000");
            assertDenied("https://localhost:3000");
        }
    }

    private void assertDenied(String origin) {
        OAuthException error = assertThrows(OAuthException.class, () -> service.requireAdminOrigin(origin));
        assertEquals("access_denied", error.getOAuthError());
        assertEquals(403, error.getStatus());
    }
}
