package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

public class AdminNativeImageUtilsTest {

    private final Logger nativeImageLogger = Logger.getLogger(NativeImageUtils.class.getName());
    private Level previousLevel;
    private boolean previousUseParentHandlers;

    @Before
    public void setUp() {
        previousLevel = nativeImageLogger.getLevel();
        previousUseParentHandlers = nativeImageLogger.getUseParentHandlers();
        nativeImageLogger.setUseParentHandlers(false);
        nativeImageLogger.setLevel(Level.OFF);
    }

    @After
    public void tearDown() {
        nativeImageLogger.setLevel(previousLevel);
        nativeImageLogger.setUseParentHandlers(previousUseParentHandlers);
    }

    @Test
    public void shouldBuildNativeImageResourceListFromAdminResource() throws Exception {
        List<String> resources = AdminNativeImageUtils.getResources(new TestAdminResource(Set.of("/admin/static/app.js")));

        assertTrue(resources.contains("/admin/static/app.js"));
        assertTrue(resources.contains(AdminConstants.ADMIN_HTML_PAGE));
        assertTrue(resources.contains(AdminConstants.ADMIN_PWA_MANIFEST_JSON));
        assertTrue(resources.contains(AdminResource.ADMIN_ASSET_MANIFEST_JSON));
        assertTrue(resources.contains(AdminConstants.BUILD_SYSTEM_INFO_MD));
        assertTrue(resources.stream().anyMatch(resource -> resource.endsWith("zh_CN.md")));
        assertTrue(resources.stream().anyMatch(resource -> resource.endsWith("en_US.md")));
    }

    @Test
    public void shouldRunNativeImageRegistrationSmoke() {
        AdminNativeImageUtils.reg(new TestAdminResource(Set.of("/admin/static/app.js")));
    }

    private static class TestAdminResource implements AdminResource {

        private final Set<String> staticResourceUris;

        private TestAdminResource(Set<String> staticResourceUris) {
            this.staticResourceUris = staticResourceUris;
        }

        @Override
        public Set<String> getAdminStaticResourceUris() {
            return staticResourceUris;
        }

        @Override
        public Set<String> getAdminPageUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminStaticCacheUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminCacheableApiUris() {
            return Set.of();
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return null;
        }

        @Override
        public String getStaticResourceBuildId() {
            return "test-build";
        }

        @Override
        public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
            return null;
        }
    }
}
