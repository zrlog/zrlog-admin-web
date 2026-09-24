package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.support.UploadFallbackZrLogConfig;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UploadControllerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final com.zrlog.common.ZrLogConfig previousConfig = Constants.zrLogConfig;
    private com.zrlog.admin.support.InMemoryZrLogDatabase permissionDb;

    @org.junit.Before
    public void openPermissionDatabase() throws Exception { permissionDb = com.zrlog.admin.support.InMemoryZrLogDatabase.open(); }

    private final String previousRootPath = System.getProperty("sws.root.path");

    @After
    public void tearDown() throws Exception {
        permissionDb.close();
        Constants.zrLogConfig = previousConfig;
        if (previousRootPath == null) {
            System.clearProperty("sws.root.path");
        } else {
            System.setProperty("sws.root.path", previousRootPath);
        }
    }

    @Test
    public void shouldRejectMissingUploadFiles() throws Exception {
        UploadController controller = controller(new HashMap<>(), new HashMap<>());

        ArgsException exception = assertThrows(ArgsException.class, controller::index);

        assertTrue(exception.getMessage().contains("imgFile"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStoreUploadedFileAndReturnLocalUrlWhenPluginCoreIsUnavailable() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
        File file = temporaryFolder.newFile("cover.png");
        Files.writeString(file.toPath(), "image", StandardCharsets.UTF_8);
        UploadController controller = controller(Map.of("dir", "image"), Map.of("imgFile", file));

        ApiStandardResponse<UploadFileResponse> response =
                (ApiStandardResponse<UploadFileResponse>) controller.index();
        String uri = response.getData().getUrl().substring("/blog".length());

        assertTrue(response.getData().getUrl().startsWith("/blog/attached/image/"));
        assertTrue(response.getData().getUrl().endsWith(".png"));
        assertEquals("image", Files.readString(PathUtil.getStaticFile(uri).toPath()));
        assertFalse(file.exists());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStoreDeprecatedThumbnailUploadThroughCommonPath() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
        File file = temporaryFolder.newFile("thumb.jpg");
        Files.writeString(file.toPath(), "thumb", StandardCharsets.UTF_8);
        UploadController controller = controller(new HashMap<>(), Map.of("file", file));

        ApiStandardResponse<UploadFileResponse> response =
                (ApiStandardResponse<UploadFileResponse>) controller.thumbnail();
        String url = response.getData().getUrl();
        String uri = url.substring("/blog".length());

        assertTrue(url.startsWith("/blog/attached/thumbnail/"));
        assertFalse(url.contains("?h="));
        assertEquals("thumb", Files.readString(PathUtil.getStaticFile(uri).toPath()));
        assertFalse(file.exists());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStoreDeprecatedThumbnailUploadInDbTemporaryDirectory() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            File file = temporaryFolder.newFile("thumb.png");
            Files.writeString(file.toPath(), "thumb", StandardCharsets.UTF_8);
            String dir = AdminConstants.ADMIN_DB_ATTACHED_TMP + "/article-cover";
            UploadController controller = controller(Map.of("dir", dir), Map.of("imgFile", file));

            ApiStandardResponse<UploadFileResponse> response =
                    (ApiStandardResponse<UploadFileResponse>) controller.thumbnail();
            String uri = response.getData().getUrl();

            assertTrue(uri.startsWith(dir + "/"));
            assertArrayEquals("thumb".getBytes(StandardCharsets.UTF_8), new DbFileService().loadDbFile(uri));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseThumbnailAsDefaultUploadDirectory() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
        File file = temporaryFolder.newFile("default.png");
        Files.writeString(file.toPath(), "image", StandardCharsets.UTF_8);
        UploadController controller = controller(new HashMap<>(), Map.of("imgFile", file));

        ApiStandardResponse<UploadFileResponse> response =
                (ApiStandardResponse<UploadFileResponse>) controller.index();

        assertTrue(response.getData().getUrl().startsWith("/blog/attached/thumbnail/"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseRequestedUploadNameWhenProvided() throws Exception {
        withRootPath();
        Constants.zrLogConfig = new UploadFallbackZrLogConfig();
        File file = temporaryFolder.newFile("source.png");
        Files.writeString(file.toPath(), "image", StandardCharsets.UTF_8);
        UploadController controller = controller(Map.of("dir", "image", "name", "cover-final.png"), Map.of("imgFile", file));

        ApiStandardResponse<UploadFileResponse> response =
                (ApiStandardResponse<UploadFileResponse>) controller.index();

        assertTrue(response.getData().getUrl().startsWith("/blog/attached/image/"));
        assertTrue(response.getData().getUrl().endsWith("/cover-final.png"));
        assertTrue(Files.exists(PathUtil.getStaticFile(response.getData().getUrl().substring("/blog".length()).toString()).toPath()));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-upload-controller").getAbsolutePath());
    }

    private static UploadController controller(Map<String, String> params, Map<String, File> files) throws Exception {
        UploadController controller = new UploadController();
        setControllerField(controller, "request", request(params, files));
        setControllerField(controller, "response", response());
        return controller;
    }

    private static void setControllerField(UploadController controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(Map<String, String> params, Map<String, File> files) {
        return (HttpRequest) Proxy.newProxyInstance(
                UploadControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getFile".equals(method.getName())) {
                        return files.get(args[0].toString());
                    }
                    if ("getParaToStr".equals(method.getName())) {
                        String key = args[0].toString();
                        if (args.length == 2) {
                            return params.getOrDefault(key, args[1].toString());
                        }
                        return params.get(key);
                    }
                    if ("getContextPath".equals(method.getName())) {
                        return "/blog";
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                UploadControllerTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }
}
