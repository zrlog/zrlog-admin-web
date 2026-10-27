package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.UploadedFile;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.FileManagerController;
import com.zrlog.admin.web.controller.api.UploadController;
import com.zrlog.admin.web.di.assets.AssetsComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.common.TokenService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class DbFileUploadAssemblyTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void productionUploadStoresChunksAndServesOnlyAuthenticatedDownloads() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openSqlite()) {
            db.config().getServerConfig().setContextPath("/sub");
            db.loadAdminWebModules();
            db.execute("UPDATE user SET secretKey=? WHERE userId=1", "test-upload-secret");
            TokenService tokens = db.component(AccountComponent.class).tokens();
            Exchange upload = new Exchange(db);
            tokens.setAdminToken(1, "test-upload-secret", "session-1", "http", upload.request(), upload.response());
            byte[] content = new byte[DbFileService.CHUNK_BYTES * 2 + 71];
            new Random(23).nextBytes(content);
            upload.file = folder.newFile("generated-request-123.bin");
            Files.write(upload.file.toPath(), content);
            String original = "原始插件包.bin";
            upload.uploadedFile = new UploadedFile("file", original, "application/octet-stream", upload.file);
            byte[] header = ("--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + original
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(header); body.write(content); body.write("\r\n--boundary--\r\n".getBytes(StandardCharsets.UTF_8));
            upload.body = body.toByteArray();
            upload.headers.put("Content-Type", "multipart/form-data; boundary=boundary");
            upload.params.put("dir", "packages");
            upload.params.put("temporary", "true");
            upload.params.put("name", "stored.bin");
            UploadController controller = (UploadController) Controller.buildController(
                    UploadController.class.getMethod("index"), upload.request(), upload.response());
            String url = controller.index().getData().getUrl();
            assertEquals(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/packages/stored.bin", url);
            DbFileMetadata meta = new Gson().fromJson(db.queryOne("SELECT remark FROM website WHERE name=?", "db_file" + url)
                    .get("remark").toString(), DbFileMetadata.class);
            assertEquals(original, meta.originalName);
            assertEquals(content.length, meta.size);
            assertTrue(meta.uploadedAt > 0);
            assertEquals(3, meta.chunkCount);

            Exchange download = new Exchange(db);
            download.uri = url;
            download.cookies.addAll(upload.cookies);
            assertFalse(db.component(AssetsComponent.class).temporaryResources().doInterceptor(download.request(), download.response()));
            assertArrayEquals(content, download.written);
            assertEquals("no-store", download.responseHeaders.get("Cache-Control"));

            Exchange anonymous = new Exchange(db);
            anonymous.uri = url;
            assertFalse(db.component(AssetsComponent.class).temporaryResources().doInterceptor(anonymous.request(), anonymous.response()));
            assertEquals(403, anonymous.code);
            assertNull(anonymous.written);

            download.params.put("path", url);
            FileManagerController files = (FileManagerController) Controller.buildController(
                    FileManagerController.class.getMethod("download"), download.request(), download.response());
            files.download();
            assertArrayEquals(content, download.written);
            assertEquals("attachment;filename=stored.bin", download.responseHeaders.get("Content-Disposition"));

            db.execute("INSERT INTO user(userId,userName,password,role,secretKey) VALUES(?,?,?,?,?)",
                    2, "other", "password", "author", "other-secret");
            Exchange other = new Exchange(db);
            other.uri = url;
            tokens.setAdminToken(2, "other-secret", "session-2", "http", other.request(), other.response());
            assertFalse(db.component(AssetsComponent.class).temporaryResources().doInterceptor(other.request(), other.response()));
            assertEquals(403, other.code);
            assertNull(other.written);

            DbFileService resources = new DbFileService(() -> new com.zrlog.model.WebSite(db.dataSource()));
            assertTrue(resources.deleteByKey(url));
            assertFalse(resources.hasRecord(url));
            assertTrue(db.queryList("SELECT name FROM website WHERE name LIKE 'db_file_chunk:%'").isEmpty());
        }
    }

    private static final class Exchange {
        final InMemoryZrLogDatabase db;
        final Map<String, String> params = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();
        final Map<String, String> responseHeaders = new HashMap<>();
        final List<Cookie> cookies = new ArrayList<>();
        String uri = "/api/admin/upload";
        File file;
        UploadedFile uploadedFile;
        byte[] body = new byte[0];
        byte[] written;
        int code;

        Exchange(InMemoryZrLogDatabase db) { this.db = db; }

        HttpRequest request() {
            return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpRequest.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "getServerConfig": return db.config().getServerConfig();
                    case "getFile": return "file".equals(a[0]) ? file : null;
                    case "getUploadedFile": return "file".equals(a[0]) ? uploadedFile : null;
                    case "getParaToStr": return params.getOrDefault(a[0], a.length > 1 ? (String) a[1] : null);
                    case "getInputStream": return new ByteArrayInputStream(body);
                    case "getHeader": return headers.get(a[0]);
                    case "getHeaderMap": return headers;
                    case "getCookies": return cookies.toArray(new Cookie[0]);
                    case "getUri": return uri;
                    case "getContextPath": return "/sub";
                    case "getMethod": return HttpMethod.POST;
                    case "getScheme": return "http";
                    case "getRemoteHost": return "127.0.0.1";
                    case "getAttr": return new HashMap<>();
                    default: return null;
                }
            });
        }

        HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpResponse.class}, (p, m, a) -> {
                if (m.getName().equals("addCookie")) cookies.add((Cookie) a[0]);
                if (m.getName().equals("addHeader")) responseHeaders.put((String) a[0], (String) a[1]);
                if (m.getName().equals("getHeader")) return responseHeaders;
                if (m.getName().equals("renderCode")) code = (Integer) a[0];
                if (m.getName().equals("write")) {
                    try (InputStream input = (InputStream) a[0]) { written = input.readAllBytes(); }
                }
                return null;
            });
        }
    }
}
