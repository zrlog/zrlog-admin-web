package com.zrlog.admin.business.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ArticleCoverUtilsTest {

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
    public void shouldReturnEmptyForEmptyContent() {
        assertEquals("", ArticleCoverUtils.getFirstImgUrl(null, null, null));
        assertEquals("", ArticleCoverUtils.getFirstImgUrl("", null, null));
    }

    @Test
    public void shouldReturnNullWhenContentHasNoImage() {
        assertNull(ArticleCoverUtils.getFirstImgUrl("<p>no image</p>", null, null));
    }

    @Test
    public void shouldCreateThumbnailForFirstLocalImage() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
        File source = PathUtil.getStaticFile("/attached/cover.png");
        source.getParentFile().mkdirs();
        Files.writeString(source.toPath(), "image", StandardCharsets.UTF_8);

        String url = ArticleCoverUtils.getFirstImgUrl("<p><img src=\"/attached/cover.png\"></p>",
                request("/blog"), null);

        assertEquals("/blog/attached/cover_thumbnail.png?h=-1&w=-1", url);
        assertEquals("image", Files.readString(PathUtil.getStaticFile("/attached/cover_thumbnail.png").toPath()));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-cover").getAbsolutePath());
    }

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
}
