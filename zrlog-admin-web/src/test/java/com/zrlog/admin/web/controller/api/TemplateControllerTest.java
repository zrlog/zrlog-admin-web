package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.TemplateDownloadResponse;
import com.zrlog.admin.business.rest.response.TemplateValuePreviewResponse;
import com.zrlog.admin.business.rest.response.UploadTemplateResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.TemplateService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.model.WebSite;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class TemplateControllerTest {

    private final String previousRootPath = System.getProperty("sws.root.path");
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private ZrLogConfig applicationConfig;

    private static void setControllerField(TemplateController controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(Map<String, String> params, String body, File file) {
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
                            return file;
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
            applicationConfig = db.config();
            ResponseRecorder response = new ResponseRecorder();
            ApiStandardResponse<Void> applied = controller(Map.of("shortTemplate", "default"), response, db.dataSource()).apply();

            assertEquals(0, applied.getError());
            assertEquals(Constants.getDefaultTemplatePath(), db.queryOne("select value from website where name=?", "template")
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

        ApiStandardResponse<Void> preview = controller(Map.of("shortTemplate", "default"), response, null).preview();

        assertEquals(0, preview.getError());
        assertEquals("template", response.cookie.getName());
        assertEquals(Constants.getDefaultTemplatePath(), response.cookie.getValue());
    }

    @Test
    public void shouldDeleteLocalTemplateDirectoryWhenPresent() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            withRootPath();
            File templateDir = new File(temporaryFolder.getRoot(), "static/include/templates/local-template");
            assertTrue(templateDir.mkdirs());
            Files.writeString(new File(templateDir, "template.properties").toPath(), "name=Local");

            DeleteResponse deleted = controller(Map.of("shortTemplate", "local-template"), new ResponseRecorder(), ignored.dataSource()).delete();

            assertTrue(deleted.getData().getDelete());
            assertFalse(templateDir.exists());
        }
    }

    @Test
    public void shouldReturnFalseWhenDeletingMissingTemplate() throws Exception {
        withRootPath();

        DeleteResponse deleted = controller(Map.of("shortTemplate", "missing-template"), new ResponseRecorder(), null).delete();

        assertFalse(deleted.getData().getDelete());
    }

    @Test
    public void shouldRejectUploadWithoutTemplateFile() throws Exception {
        ArgsException exception = assertThrows(ArgsException.class,
                () -> controller(Map.of(), new ResponseRecorder(), null).upload());

        assertTrue(exception.getMessage().contains("file"));
    }

    @Test
    public void shouldInstallUploadedThemeAndRecordAudit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            withRootPath();
            File zipFile = temporaryFolder.newFile("controller-theme.zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
                output.putNextEntry(new ZipEntry("template.properties"));
                output.write("name=Controller Theme\nversion=1.0.0\n".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
                output.putNextEntry(new ZipEntry("index.ftl"));
                output.write("<html></html>".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }

            UploadTemplateResponse response = controllerWithFile(Map.of(
                    "shortTemplate", "controller-theme",
                    "overwrite", "false"
            ), zipFile, new ResponseRecorder(), db.dataSource()).upload();

            assertEquals(0, response.getError());
            assertEquals("controller-theme", response.getData().getShortTemplate());
            assertTrue(new File(temporaryFolder.getRoot(),
                    "static/include/templates/controller-theme/index.ftl").exists());
            String audit = String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value"));
            assertTrue(audit.contains("UPLOAD_TEMPLATE"));
            assertTrue(audit.contains("controller-theme"));
        }
    }

    @Test
    public void shouldIgnoreConfigUpdateWhenTemplateIsBlank() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            assertNotNull(controller(Map.of(), "{\"template\":\"\"}", new ResponseRecorder(), ignored.dataSource()).config());
        }
    }

    @Test
    public void shouldPersistTemplateConfigAndAuditThroughRealDao() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            assertNotNull(controller(Map.of(), "{\"template\":\"" + Constants.getDefaultTemplatePath() + "\"}",
                    new ResponseRecorder(), db.dataSource()).config());

            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("UPDATE_TEMPLATE_CONFIG"));
        }
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromRefererHost() throws Exception {
        TemplateController controller = controller(Map.of("referer", "https://example.com/admin/template"),
                new ResponseRecorder(), null);

        TemplateDownloadResponse download = controller.templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://example.com/blog/admin/template"));
        assertTrue(download.getUrl().contains("upgrade-v3=true"));
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromRequestHostWhenRefererIsMissing() throws Exception {
        TemplateDownloadResponse download = controller(Map.of("Host", "host.example:8080"),
                new ResponseRecorder(), null).templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://host.example:8080/blog/admin/template"));
    }

    @Test
    public void shouldReturnDownloadCenterUrlFromExplicitHostParam() throws Exception {
        TemplateDownloadResponse download = controller(Map.of("host", "param.example"),
                new ResponseRecorder(), null).templateCenter().getData();

        assertTrue(download.getUrl().contains("from=http://param.example/blog/admin/template"));
    }

    @Test
    public void shouldPreviewTemplateConfigValue() throws Exception {
        ApiStandardResponse<TemplateValuePreviewResponse> response =
                controller(Map.of("value", "<p>Hello</p>"), new ResponseRecorder(), null).previewConfigValue();

        assertNotNull(response.getData().getPreviewValue());
        assertTrue(response.getData().getPreviewValue().contains("Hello"));
    }

    private void withRootPath() throws Exception {
        System.setProperty("sws.root.path", temporaryFolder.getRoot().getAbsolutePath());
    }

    private TemplateController controller(Map<String, String> params, ResponseRecorder response, DataSourceWrapper testDataSource)
            throws Exception {
        return controller(params, null, response, testDataSource);
    }

    private TemplateController controller(Map<String, String> params, String body, ResponseRecorder response, DataSourceWrapper testDataSource)
            throws Exception {
        return controller(params, body, null, response, testDataSource);
    }

    private TemplateController controllerWithFile(Map<String, String> params, File file,
                                                  ResponseRecorder response, DataSourceWrapper testDataSource)
            throws Exception {
        return controller(params, null, file, response, testDataSource);
    }

    private TemplateController controller(Map<String, String> params, String body, File file,
                                          ResponseRecorder response, DataSourceWrapper testDataSource) throws Exception {
        TemplateController controller =
                new TemplateController(
                        new TemplateService(
                                new InMemoryZrLogDatabase.TestCacheService(),
                                () -> null,
                                () -> null,
                                ApplicationServicesFixture.audit(testDataSource),
                                () -> new WebSite(testDataSource)),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource)),
                        AccountServicesFixture.component(testDataSource).requestIdentity());
        setControllerField(controller, "request", request(params, body, file));
        setControllerField(controller, "response", response.response());
        return controller;
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
