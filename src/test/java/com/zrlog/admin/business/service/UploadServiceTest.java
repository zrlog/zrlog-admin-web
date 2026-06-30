package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.support.UploadFallbackZrLogConfig;
import com.zrlog.common.Constants;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UploadServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final com.zrlog.common.ZrLogConfig previousConfig = Constants.zrLogConfig;
    private final String previousRootPath = System.getProperty("sws.root.path");

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
        if (previousRootPath == null) {
            System.clearProperty("sws.root.path");
        } else {
            System.setProperty("sws.root.path", previousRootPath);
        }
    }

    @Test
    public void shouldFallbackToLocalUrlWhenPluginCoreIsUnavailable() {
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();

        UploadFileResponse response = new UploadService().getCloudUrl(
                "/attached/a.png", "/tmp/a.png", request("/blog"), null);

        assertEquals("/blog/attached/a.png", response.getUrl());
    }

    @Test
    public void shouldSaveBytesWithNormalizedExtension() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();

        UploadFileResponse response = new UploadService().saveBytes(
                "hello".getBytes(StandardCharsets.UTF_8), "", "image", request("/blog"), null);
        String uri = response.getUrl().substring("/blog".length());
        File file = PathUtil.getStaticFile(uri);

        assertTrue(response.getUrl().startsWith("/blog/attached/image/"));
        assertTrue(response.getUrl().endsWith(".png"));
        assertEquals("hello", Files.readString(file.toPath()));
    }

    @Test
    public void shouldSaveThumbnailBytesWithStableMd5NameAndDimensions() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();

        UploadFileResponse response = new UploadService().saveThumbnailBytes(
                "abc".getBytes(StandardCharsets.UTF_8), "jpg", request(""), null);
        String uri = response.getUrl().substring(0, response.getUrl().indexOf("?"));
        File file = PathUtil.getStaticFile(uri);

        assertTrue(uri.contains("/attached/thumbnail/"));
        assertTrue(uri.endsWith("/900150983cd24fb0d6963f7d28e17f72.jpg"));
        assertEquals("?h=-1&w=-1", response.getUrl().substring(response.getUrl().indexOf("?")));
        assertEquals("abc", Files.readString(file.toPath()));
    }

    @Test
    public void shouldNormalizeExtensions() throws Exception {
        UploadService service = new UploadService();

        assertEquals("png", service.normalizeExtension(null));
        assertEquals("png", service.normalizeExtension(" "));
        assertEquals("webp", service.normalizeExtension("webp"));
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
