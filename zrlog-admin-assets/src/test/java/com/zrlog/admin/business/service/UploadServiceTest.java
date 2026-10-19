package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UploadServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final String previousRootPath = System.getProperty("sws.root.path");

    @After
    public void tearDown() {
        if (previousRootPath == null) {
            System.clearProperty("sws.root.path");
        } else {
            System.setProperty("sws.root.path", previousRootPath);
        }
    }

    @Test
    public void shouldFallbackToLocalUrlWhenPluginCoreIsUnavailable() {
        UploadFileResponse response = new UploadService(() -> null).getCloudUrl(
                "/attached/a.png", "/tmp/a.png", request("/blog"), null);

        assertEquals("/blog/attached/a.png", response.getUrl());
    }

    @Test
    public void shouldSaveBytesWithNormalizedExtension() throws Exception {
        withRootPath();

        UploadFileResponse response = new UploadService(() -> null).saveBytes(
                "hello".getBytes(StandardCharsets.UTF_8), "", "image", request("/blog"), null);
        String uri = response.getUrl().substring("/blog".length());
        File file = PathUtil.getStaticFile(uri);

        assertTrue(response.getUrl().startsWith("/blog/attached/image/"));
        assertTrue(response.getUrl().endsWith(".png"));
        assertEquals("hello", Files.readString(file.toPath()));
    }

    @Test
    public void shouldSaveThumbnailBytesWithStableMd5Name() throws Exception {
        withRootPath();

        UploadFileResponse response = new UploadService(() -> null).saveThumbnailBytes(
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
        UploadService service = new UploadService(() -> null);

        assertEquals("png", service.normalizeExtension(null));
        assertEquals("png", service.normalizeExtension(" "));
        assertEquals("webp", service.normalizeExtension("webp"));
    }

    @Test
    public void shouldNormalizeTemporaryUploadDirectories() throws Exception {
        UploadService service = new UploadService(() -> null);

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
        UploadService service = new UploadService(() -> null);

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
