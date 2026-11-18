package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.zrlog.admin.business.AdminConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AdminResourceLazyLoadingTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalStaticPath;
    private Path icon;

    @Before
    public void useTemporaryStaticResources() throws Exception {
        originalStaticPath = System.getProperty("sws.static.path");
        Path staticPath = temporaryFolder.newFolder("static").toPath();
        System.setProperty("sws.static.path", staticPath.toString());
        icon = staticPath.resolve(AdminConstants.FAVICON_PNG_PWA_192_URI_PATH.substring(1));
        Files.createDirectories(icon.getParent());
    }

    @After
    public void restoreStaticResources() {
        if (originalStaticPath == null) System.clearProperty("sws.static.path");
        else System.setProperty("sws.static.path", originalStaticPath);
    }

    @Test
    public void routesAndServiceWorkerDoNotNeedIconHashesAndFailedVersionReadsCanRetry()
            throws Exception {
        // A directory cannot be read as an icon; route registration must still succeed.
        Files.createDirectory(icon);
        var resources = new AdminResourceImpl("/blog", () -> 0L, request -> "/blog");

        assertTrue(resources.getAdminPageUris().contains("/blog/admin/index"));
        assertTrue(resources.getAdminCacheableApiUris().contains("/api/admin/website"));
        assertTrue(
                resources
                        .getAdminStaticCacheUris()
                        .contains("/blog" + AdminConstants.ADMIN_SERVICE_WORKER_JS));
        try (var worker = resources.renderServiceWorker(null)) {
            String script = new String(worker.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(script.contains("/blog/admin/static/"));
            assertFalse(script.contains("/blog/admin/index?"));
        }
        RuntimeException failure =
                assertThrows(RuntimeException.class, resources::getStaticResourceBuildId);
        assertTrue(failure.getCause() instanceof IOException);

        Files.delete(icon);
        Files.writeString(icon, "available icon bytes");
        String expected =
                new AdminResourceImpl("/blog", () -> 0L, request -> "/blog")
                        .getStaticResourceBuildId();
        assertEquals(expected, resources.getStaticResourceBuildId());
    }

    @Test
    public void concurrentFirstRequestsCacheCurrentAssetsAndKeepSiteVersionLive() throws Exception {
        Files.writeString(icon, "icon before startup");
        AtomicLong siteVersion = new AtomicLong(10);
        var resources = new AdminResourceImpl("/blog", siteVersion::get, request -> "/blog");
        Files.writeString(icon, "icon at first request");
        var expected = new AdminResourceImpl("/blog", siteVersion::get, request -> "/blog");
        String expectedVersion = expected.getStaticResourceBuildId();
        var expectedUris = expected.getAdminStaticResourceUris();

        int clients = 8;
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(clients);
        try {
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < clients; i++) {
                results.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    assertEquals(
                                            expectedUris, resources.getAdminStaticResourceUris());
                                    assertTrue(
                                            resources
                                                    .getAdminCacheableApiUris()
                                                    .contains("/api/admin/website"));
                                    return resources.getStaticResourceBuildId();
                                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var result : results)
                assertEquals(expectedVersion, result.get(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Files.writeString(icon, "icon after initialization");
        assertEquals(expectedVersion, resources.getStaticResourceBuildId());
        assertNotEquals(
                expectedVersion,
                new AdminResourceImpl("/blog", siteVersion::get, request -> "/blog")
                        .getStaticResourceBuildId());
        siteVersion.incrementAndGet();
        assertEquals(
                Long.toString(Long.parseLong(expectedVersion) + 1),
                resources.getStaticResourceBuildId());
    }
}
