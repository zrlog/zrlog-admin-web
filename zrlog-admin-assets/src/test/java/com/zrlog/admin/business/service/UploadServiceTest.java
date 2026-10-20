package com.zrlog.admin.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;

import org.junit.After;
import org.junit.Before;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UploadServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String previousRootPath = System.getProperty("sws.root.path");
    private InMemoryZrLogDatabase database;

    @Before
    public void setUpIdentity() throws Exception { database = InMemoryZrLogDatabase.open(); }


    @After
    public void tearDown() throws Exception {
        database.close();
        if (previousRootPath == null) {
            System.clearProperty("sws.root.path");
        } else {
            System.setProperty("sws.root.path", previousRootPath);
        }
    }

    @Test
    public void shouldFallbackToLocalUrlWhenPluginCoreIsUnavailable() {
        UploadFileResponse response =
                new UploadService(
                                () -> null,
                                AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }))
                        .getCloudUrl("/attached/a.png", "/tmp/a.png", request("/blog"), null);

        assertEquals("/blog/attached/a.png", response.getUrl());
    }

    @Test
    public void shouldSaveBytesWithNormalizedExtension() throws Exception {
        withRootPath();

        UploadFileResponse response =
                new UploadService(
                                () -> null,
                                AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }))
                        .saveAvatarBytes(
                                AccountServicesFixture.component(database.dataSource()).requestIdentity().current(),
                                "hello".getBytes(StandardCharsets.UTF_8),
                                "",
                                request("/blog"),
                                null);
        String uri = response.getUrl().substring("/blog".length());
        File file = PathUtil.getStaticFile(uri);

        assertTrue(response.getUrl().startsWith("/blog/attached/image/"));
        assertTrue(response.getUrl().endsWith(".png"));
        assertEquals("hello", Files.readString(file.toPath()));
    }

    @Test
    public void shouldSaveThumbnailBytesWithStableMd5Name() throws Exception {
        withRootPath();

        UploadFileResponse response =
                new UploadService(
                                () -> null,
                                AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }))
                        .saveThumbnailBytes(
                                AccountServicesFixture.component(database.dataSource()).requestIdentity().current(),
                                "abc".getBytes(StandardCharsets.UTF_8), "jpg", request(""), null);
        String uri = response.getUrl();
        File file = PathUtil.getStaticFile(uri);

        assertTrue(uri.contains("/attached/thumbnail/"));
        assertTrue(uri.endsWith("/900150983cd24fb0d6963f7d28e17f72.jpg"));
        assertFalse(response.getUrl().contains("?h="));
        assertEquals("abc", Files.readString(file.toPath()));
    }

    @Test
    public void shouldNormalizeExtensions() throws Exception {
        UploadService service =
                new UploadService(
                        () -> null,
                        AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                        new DbFileService(ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }));

        assertEquals("png", service.normalizeExtension(null));
        assertEquals("png", service.normalizeExtension(" "));
        assertEquals("webp", service.normalizeExtension("webp"));
    }

    @Test
    public void shouldNormalizeTemporaryUploadDirectories() throws Exception {
        UploadService service =
                new UploadService(
                        () -> null,
                        AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                        new DbFileService(ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }));

        assertEquals("/", service.normalizeTemporaryDir(AdminConstants.ADMIN_DB_ATTACHED_TMP));
        assertEquals("/", service.normalizeTemporaryDir(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/"));
        assertEquals("/nested/path", service.normalizeTemporaryDir(AdminConstants.ADMIN_DB_ATTACHED_TMP + "\\nested//path"));
        assertNull(service.normalizeTemporaryDir("/attached/normal"));
        assertNull(service.normalizeTemporaryDir(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/../escape"));
        assertNull(service.normalizeTemporaryDir(null));
    }

    @Test
    public void shouldBuildTemporaryUriUnderAdminDbAttachmentRoot() throws Exception {
        File file = temporaryFolder.newFile("cover.PNG");
        Files.writeString(file.toPath(), "image", StandardCharsets.UTF_8);
        UploadService service =
                new UploadService(
                        () -> null,
                        AccountServicesFixture.component(database.dataSource()).identities(), AccountServicesFixture.component(database.dataSource()).permissions(),
                        new DbFileService(ApplicationServicesFixture.websiteKv(null), () -> { throw new IllegalStateException("Database is unavailable in this test"); }));

        String rootUri = service.buildTemporaryUri(AdminConstants.ADMIN_DB_ATTACHED_TMP, file);
        String nestedUri = service.buildTemporaryUri(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/article-cover", file);

        assertTrue(rootUri.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/"));
        assertTrue(rootUri.endsWith(".png"));
        assertTrue(nestedUri.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/article-cover/"));
        assertTrue(nestedUri.endsWith(".png"));
        assertNull(service.buildTemporaryUri("/attached/normal", file));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-upload").getAbsolutePath());
    }

    private static HttpRequest request(String contextPath) {
        return (HttpRequest) Proxy.newProxyInstance(
                UploadServiceTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }
}
