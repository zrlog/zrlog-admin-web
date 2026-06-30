package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.TemplateDownloadResponse;
import com.zrlog.admin.business.rest.response.TemplateValuePreviewResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TemplateControllerTest {

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
    public void shouldApplyDefaultTemplateAndClearPreviewCookie() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            ResponseRecorder response = new ResponseRecorder();
            ApiStandardResponse<Void> applied = controller(Map.of("shortTemplate", "default"), response).apply();

            assertEquals(0, applied.getError());
            assertEquals(Constants.DEFAULT_TEMPLATE_PATH, db.queryOne("select value from website where name=?", "template")
                    .get("value"));
            assertEquals("template", response.cookie.getName());
            assertEquals("", response.cookie.getValue());
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("APPLY_TEMPLATE"));
        }
    }

    @Test
    public void shouldSetPreviewCookieForDefaultTemplate() throws Exception {
        ResponseRecorder response = new ResponseRecorder();

        ApiStandardResponse<Void> preview = controller(Map.of("shortTemplate", "default"), response).preview();

        assertEquals(0, preview.getError());
        assertEquals("template", response.cookie.getName());
        assertEquals(Constants.DEFAULT_TEMPLATE_PATH, response.cookie.getValue());
    }

    @Test
    public void shouldDeleteLocalTemplateDirectoryWhenPresent() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            withRootPath();
            File templateDir = new File(temporaryFolder.getRoot(), "static/include/templates/local-template");
            assertTrue(templateDir.mkdirs());
            Files.writeString(new File(templateDir, "template.properties").toPath(), "name=Local");

            DeleteResponse deleted = controller(Map.of("shortTemplate", "local-template"), new ResponseRecorder()).delete();

            assertTrue(deleted.getData().getDelete());
            assertFalse(templateDir.exists());
        }
    }

    @Test
    public void shouldReturnFalseWhenDeletingMissingTemplate() throws Exception {
        withRootPath();

        DeleteResponse deleted = controller(Map.of("shortTemplate", "missing-template"), new ResponseRecorder()).delete();

        assertFalse(deleted.getData().getDelete());
    }

    @Test
    public void shouldRejectUploadWithoutTemplateFile() throws Exception {
        ArgsException exception = assertThrows(ArgsException.class,
                () -> controller(Map.of(), new ResponseRecorder()).upload());

        assertTrue(exception.getMessage().contains("file"));
    }

    @Test
    public void shouldIgnoreConfigUpdateWhenTemplateIsBlank() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            assertNotNull(controller(Map.of(), "{\"template\":\"\"}", new ResponseRecorder()).config());
        }
    }

    @Test
    public void shouldPersistTemplateConfigAndAuditThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            assertNotNull(controller(Map.of(), "{\"template\":\"" + Constants.DEFAULT_TEMPLATE_PATH + "\"}",
                    new ResponseRecorder()).config());

            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("UPDATE_TEMPLATE_CONFIG"));
        }
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromRefererHost() throws Exception {
        TemplateController controller = controller(Map.of("referer", "https://example.com/admin/template"),
                new ResponseRecorder());

        TemplateDownloadResponse download = controller.templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://example.com/blog/admin/template"));
        assertTrue(download.getUrl().contains("upgrade-v3=true"));
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromRequestHostWhenRefererIsMissing() throws Exception {
        TemplateDownloadResponse download = controller(Map.of("Host", "host.example:8080"),
                new ResponseRecorder()).templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://host.example:8080/blog/admin/template"));
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromExplicitHostParam() throws Exception {
        TemplateDownloadResponse download = controller(Map.of("host", "param.example"),
                new ResponseRecorder()).templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://param.example/blog/admin/template"));
    }

    @Test
    public void shouldPreviewTemplateConfigValue() throws Exception {
        ApiStandardResponse<TemplateValuePreviewResponse> response =
                controller(Map.of("value", "<p>Hello</p>"), new ResponseRecorder()).previewConfigValue();

        assertNotNull(response.getData().getPreviewValue());
        assertTrue(response.getData().getPreviewValue().contains("Hello"));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    private static TemplateController controller(Map<String, String> params, ResponseRecorder response)
            throws Exception {
        return controller(params, null, response);
    }

    private static TemplateController controller(Map<String, String> params, String body, ResponseRecorder response)
            throws Exception {
        TemplateController controller = new TemplateController();
        setControllerField(controller, "request", request(params, body));
        setControllerField(controller, "response", response.response());
        return controller;
    }

    private static void setControllerField(TemplateController controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(Map<String, String> params, String body) {
        return (HttpRequest) Proxy.newProxyInstance(
                TemplateControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getParaToStr":
                            if (args.length == 2) {
                                return params.getOrDefault(args[0].toString(), args[1].toString());
                            }
                            return params.get(args[0].toString());
                        case "getHeader":
                            return params.get(args[0].toString());
                        case "getContextPath":
                            return "/blog";
                        case "getCreateTime":
                            return System.currentTimeMillis();
                        case "getUri":
                            return "/api/admin/template";
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getInputStream":
                            if (body == null) {
                                return null;
                            }
                            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        case "getFile":
                            return null;
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (method.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static class ResponseRecorder {

        private Cookie cookie;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    TemplateControllerTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addCookie".equals(method.getName())) {
                            cookie = (Cookie) args[0];
                        }
                        if ("getHeader".equals(method.getName())) {
                            return new HashMap<String, String>();
                        }
                        return null;
                    });
        }
    }
}
