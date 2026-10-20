package com.zrlog.admin.business.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ArticleCoverUtilsTest {

    private final String previousRootPath = System.getProperty("sws.root.path");
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static HttpRequest request(String contextPath) {
        return (HttpRequest) Proxy.newProxyInstance(
                ArticleCoverUtilsTest.class.getClassLoader(),
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

    @After
    public void tearDown() {
        if (previousRootPath == null) {
            System.clearProperty("sws.root.path");
        } else {
            System.setProperty("sws.root.path", previousRootPath);
        }
    }

    @Test
    public void shouldReturnEmptyForEmptyContent() {
        assertEquals(
                "",
                ArticleCoverUtils.getFirstImgUrl(
                        null,
                        null,
                        null,
                        new UploadService(
                                () -> null,
                                AccountServicesFixture.component(null).identities(), AccountServicesFixture.component(null).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }))));
        assertEquals(
                "",
                ArticleCoverUtils.getFirstImgUrl(
                        "",
                        null,
                        null,
                        new UploadService(
                                () -> null,
                                AccountServicesFixture.component(null).identities(), AccountServicesFixture.component(null).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }))));
    }

    @Test
    public void shouldReturnNullWhenContentHasNoImage() {
        assertNull(
                ArticleCoverUtils.getFirstImgUrl(
                        "<p>no image</p>",
                        null,
                        null,
                        new UploadService(
                                () -> null,
                                AccountServicesFixture.component(null).identities(), AccountServicesFixture.component(null).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                }))));
    }

    @Test
    public void shouldCreateThumbnailForFirstLocalImage() throws Exception {
        withRootPath();
        File source = PathUtil.getStaticFile("/attached/cover.png");
        source.getParentFile().mkdirs();
        Files.writeString(source.toPath(), "image", StandardCharsets.UTF_8);

        String url =
                ArticleCoverUtils.getFirstImgUrl(
                        "<p><img src=\"/attached/cover.png\"></p>",
                        request("/blog"),
                        null,
                        new UploadService(
                                () -> null,
                                AccountServicesFixture.component(null).identities(), AccountServicesFixture.component(null).permissions(),
                                new DbFileService(
                                        ApplicationServicesFixture.websiteKv(null), () -> {
                                    throw new IllegalStateException("Database is unavailable in this test");
                                })));

        assertEquals("/blog/attached/cover_thumbnail.png?h=-1&w=-1", url);
        assertEquals("image", Files.readString(PathUtil.getStaticFile("/attached/cover_thumbnail.png").toPath()));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-cover").getAbsolutePath());
    }
}
