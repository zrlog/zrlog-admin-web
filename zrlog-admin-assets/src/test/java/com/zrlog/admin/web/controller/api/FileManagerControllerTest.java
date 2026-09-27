package com.zrlog.admin.web.controller.api;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.request.ReplaceArticleResourceUrlRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.rest.response.FileManagerResponse;
import com.zrlog.admin.business.rest.response.ReplaceArticleResourceUrlResponse;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.FileManagerService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FileManagerControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSearchThroughFileManagerService() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        FileManagerController controller = controller(Map.of("key", "logo"), service, new ResponseRecorder());

        ApiStandardResponse<List<FileEntryVO>> response =
                (ApiStandardResponse<List<FileEntryVO>>) controller.search();

        assertEquals("logo", service.searchKey);
        assertEquals(1, response.getData().size());
        assertEquals("logo.png", response.getData().get(0).getName());
    }

    @Test
    public void shouldListShortcutsAndRefreshReferenceIndexThroughRealService() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            FileManagerController listController = realController(Map.of("path", ""), new ResponseRecorder());
            FileManagerController brokenController = realController(Map.of("resourceType", "broken", "key", "missing"),
                    new ResponseRecorder());
            FileManagerController refreshController = realController(Map.of(), new ResponseRecorder());

            AdminPageDataResponse<FileManagerResponse> list = listController.index();
            AdminPageDataResponse<FileManagerResponse> broken = brokenController.index();
            ApiStandardResponse<Boolean> refreshed = refreshController.refreshReferenceIndex();

            assertTrue(list.getData().getShortcuts().stream()
                    .anyMatch(entry -> "/attached".equals(entry.getPath())));
            assertTrue(list.getData().getEntries().stream()
                    .anyMatch(entry -> "/attached".equals(entry.getPath())));
            assertEquals(List.of(), broken.getData().getEntries());
            assertEquals(List.of(), broken.getData().getDirectoryActions());
            assertEquals(Boolean.FALSE, refreshed.getData());
        }
    }

    @Test
    public void shouldValidateActionParametersBeforeCallingService() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        FileManagerController controller = controller(new HashMap<>(), service, new ResponseRecorder());

        assertThrows(ArgsException.class, controller::delete);
        assertThrows(ArgsException.class, controller::mkdir);
        assertThrows(ArgsException.class, controller::rename);
        assertThrows(ArgsException.class, controller::reuploadMissingLocalResource);
        assertThrows(ArgsException.class, () -> controller(Map.of("path", "/attached/missing.png"), service,
                new ResponseRecorder()).reuploadMissingLocalResource());

        assertNull(service.deletedPath);
        assertNull(service.mkdirPath);
        assertNull(service.renamedPath);
    }

    @Test
    public void shouldReturnFalseForNoopFileActionsWithoutAuditing() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        FileManagerController deleteController = controller(Map.of("path", "/attached/missing.txt"), service,
                new ResponseRecorder());
        FileManagerController mkdirController = controller(Map.of("path", "/attached/existing"), service,
                new ResponseRecorder());
        FileManagerController renameController = controller(Map.of(
                "path", "/attached/a.txt",
                "newName", "b.txt",
                "syncArticleReferences", "true"), service, new ResponseRecorder());

        assertEquals(Boolean.FALSE, deleteController.delete().getData());
        assertEquals(Boolean.FALSE, mkdirController.mkdir().getData());
        assertEquals(Boolean.FALSE, renameController.rename().getData());

        assertEquals("/attached/missing.txt", service.deletedPath);
        assertEquals("/attached/existing", service.mkdirPath);
        assertEquals("/attached/a.txt", service.renamedPath);
        assertEquals("b.txt", service.newName);
        assertTrue(service.syncArticleReferences);
    }

    @Test
    public void shouldAuditSuccessfulFileActionsThroughRealStore() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FakeFileManagerService service = new FakeFileManagerService();
            service.actionResult = true;

            assertEquals(Boolean.TRUE, controller(Map.of("path", "/attached/delete.txt"), service,
                    new ResponseRecorder()).delete().getData());
            assertEquals(Boolean.TRUE, controller(Map.of("path", "/attached/new-dir"), service,
                    new ResponseRecorder()).mkdir().getData());
            assertEquals(Boolean.TRUE, controller(Map.of("path", "/attached/a.txt", "newName", "b.txt"),
                    service, new ResponseRecorder()).rename().getData());

            String audit = String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value"));
            assertTrue(audit.contains("DELETE_FILE"));
            assertTrue(audit.contains("CREATE_DIRECTORY"));
            assertTrue(audit.contains("RENAME_FILE"));
        }
    }

    @Test
    public void shouldAuditSuccessfulReuploadThroughRealStore() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FakeFileManagerService service = new FakeFileManagerService();
            service.uploadResponse = new UploadFileResponse("/attached/missing.png");
            File upload = File.createTempFile("zrlog-upload", ".png");
            upload.deleteOnExit();

            ApiStandardResponse<UploadFileResponse> response = controller(
                    Map.of("path", "/attached/missing.png"), null, Map.of("imgFile", upload),
                    service, new ResponseRecorder()).reuploadMissingLocalResource();

            assertEquals("/attached/missing.png", response.getData().getUrl());
            assertEquals("/attached/missing.png", service.reuploadPath);
            assertTrue(String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value")).contains("REUPLOAD_MISSING_FILE"));
        }
    }

    @Test
    public void shouldRecordReplaceArticleResourceUrlThroughRealStores() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FakeFileManagerService service = new FakeFileManagerService();
            ReplaceArticleResourceUrlResponse replaceResponse = new ReplaceArticleResourceUrlResponse();
            replaceResponse.setScannedArticles(3);
            replaceResponse.setUpdatedArticles(2);
            replaceResponse.setUpdatedFields(4);
            service.replaceResponse = replaceResponse;

            ApiStandardResponse<ReplaceArticleResourceUrlResponse> response = controller(
                    Map.of(), "{\"fromUrl\":\"/attached/old.png\",\"toUrl\":\"/attached/new.png\"}",
                    Map.of(), service, new ResponseRecorder()).replaceArticleResourceUrl();

            assertEquals(3, response.getData().getScannedArticles());
            assertEquals("/attached/old.png", service.replaceRequest.getFromUrl());
            String audit = String.valueOf(db.queryOne("select value from website where name=?", "admin_audit_log")
                    .get("value"));
            assertTrue(audit.contains("REPLACE_ARTICLE_RESOURCE_URL"));
            assertTrue(audit.contains("/attached/old.png"));
            assertTrue(audit.contains("/attached/new.png"));
        }
    }

    @Test
    public void shouldReadContentThroughFileManagerService() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        service.content = "hello";
        FileManagerController controller = controller(Map.of("path", "/attached/a.txt"), service,
                new ResponseRecorder());

        assertEquals("hello", controller.readContent().getData());
        assertEquals("hello", controller.read().getData());
        assertEquals("/attached/a.txt", service.readContentPath);
    }

    @Test
    public void shouldRedirectExternalDownloads() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        ResponseRecorder recorder = new ResponseRecorder();
        FileManagerController httpsController = controller(Map.of("path", "https://cdn.example.com/a.png"),
                service, recorder);

        httpsController.download();

        assertEquals("https://cdn.example.com/a.png", recorder.redirect);
        assertNull(service.readPath);

        ResponseRecorder protocolRelative = new ResponseRecorder();
        controller(Map.of("path", "//cdn.example.com/a.png"), service, protocolRelative).download();
        assertEquals("https://cdn.example.com/a.png", protocolRelative.redirect);
    }

    @Test
    public void shouldDownloadLocalFilesWithAttachmentHeader() throws Exception {
        FakeFileManagerService service = new FakeFileManagerService();
        service.bytes = "download".getBytes(StandardCharsets.UTF_8);
        ResponseRecorder recorder = new ResponseRecorder();
        FileManagerController controller = controller(Map.of("path", "/attached/a.txt"), service, recorder);

        controller.download();

        assertEquals("/attached/a.txt", service.readPath);
        assertEquals("attachment;filename=a.txt", recorder.headers.get("Content-Disposition"));
        assertArrayEquals("download".getBytes(StandardCharsets.UTF_8), recorder.writtenBytes);
    }

    private static FileManagerController controller(Map<String, String> params, FakeFileManagerService service,
                                                    ResponseRecorder responseRecorder) throws Exception {
        return controller(params, null, Map.of(), service, responseRecorder);
    }

    private static FileManagerController controller(Map<String, String> params, String body, Map<String, File> files,
                                                    FakeFileManagerService service,
                                                    ResponseRecorder responseRecorder) throws Exception {
        FileManagerController controller = new FileManagerController();
        setControllerField(controller, "request", request(params, body, files));
        setControllerField(controller, "response", responseRecorder.response());
        Field serviceField = FileManagerController.class.getDeclaredField("fileManagerService");
        serviceField.setAccessible(true);
        serviceField.set(controller, service);
        return controller;
    }

    private static FileManagerController realController(Map<String, String> params, ResponseRecorder responseRecorder)
            throws Exception {
        FileManagerController controller = new FileManagerController();
        setControllerField(controller, "request", request(params));
        setControllerField(controller, "response", responseRecorder.response());
        return controller;
    }

    private static void setControllerField(FileManagerController controller, String name, Object value)
            throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(Map<String, String> params) {
        return request(params, null, Map.of());
    }

    private static HttpRequest request(Map<String, String> params, String body, Map<String, File> files) {
        return (HttpRequest) Proxy.newProxyInstance(
                FileManagerControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getParaToStr".equals(method.getName())) {
                        String key = args[0].toString();
                        if (args.length == 2) {
                            return params.getOrDefault(key, args[1].toString());
                        }
                        return params.get(key);
                    }
                    if ("getParaToBool".equals(method.getName())) {
                        return Boolean.parseBoolean(params.getOrDefault(args[0].toString(), args[1].toString()));
                    }
                    if ("getUri".equals(method.getName())) {
                        return "/api/admin/file-manager";
                    }
                    if ("getInputStream".equals(method.getName())) {
                        if (body == null) {
                            return null;
                        }
                        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    }
                    if ("getFile".equals(method.getName())) {
                        return files.get(args[0].toString());
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class FakeFileManagerService extends FileManagerService {

        private String searchKey;
        private String deletedPath;
        private String mkdirPath;
        private String renamedPath;
        private String newName;
        private boolean syncArticleReferences;
        private String readContentPath;
        private String readPath;
        private String content = "";
        private byte[] bytes = new byte[0];
        private boolean actionResult;
        private String reuploadPath;
        private UploadFileResponse uploadResponse = new UploadFileResponse("/attached/upload.png");
        private ReplaceArticleResourceUrlRequest replaceRequest;
        private ReplaceArticleResourceUrlResponse replaceResponse = new ReplaceArticleResourceUrlResponse();

        @Override
        public List<FileEntryVO> search(String key) {
            searchKey = key;
            return List.of(new FileEntryVO("logo.png", "/attached/logo.png", "file", 10, "image/png", 0));
        }

        @Override
        public boolean delete(String path) {
            deletedPath = path;
            return actionResult;
        }

        @Override
        public boolean mkdir(String path) {
            mkdirPath = path;
            return actionResult;
        }

        @Override
        public boolean rename(String path, String newName, boolean syncArticleReferences,
                              com.zrlog.common.vo.AdminTokenVO user) {
            renamedPath = path;
            this.newName = newName;
            this.syncArticleReferences = syncArticleReferences;
            return actionResult;
        }

        @Override
        public String readContent(String path) {
            readContentPath = path;
            return content;
        }

        @Override
        public byte[] read(String path) {
            readPath = path;
            return bytes;
        }

        @Override
        public UploadFileResponse reuploadMissingLocalResource(String path, File uploadFile, HttpRequest request,
                                                               com.zrlog.common.vo.AdminTokenVO user) {
            reuploadPath = path;
            return uploadResponse;
        }

        @Override
        public ReplaceArticleResourceUrlResponse replaceArticleResourceUrl(
                com.zrlog.common.vo.AdminTokenVO user, ReplaceArticleResourceUrlRequest request) {
            replaceRequest = request;
            return replaceResponse;
        }
    }

    private static class ResponseRecorder {

        private String redirect;
        private final Map<String, String> headers = new HashMap<>();
        private byte[] writtenBytes;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    FileManagerControllerTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("redirect".equals(method.getName())) {
                            redirect = args[0].toString();
                        } else if ("addHeader".equals(method.getName())) {
                            headers.put(args[0].toString(), args[1].toString());
                        } else if ("write".equals(method.getName()) && args.length == 1) {
                            writtenBytes = ((InputStream) args[0]).readAllBytes();
                        }
                        return null;
                    });
        }
    }
}
