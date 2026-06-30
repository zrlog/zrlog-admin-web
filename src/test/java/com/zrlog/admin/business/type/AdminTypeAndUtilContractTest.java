package com.zrlog.admin.business.type;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity;
import com.zrlog.admin.util.AdminStaticSiteProgress;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.util.DevKit;
import com.zrlog.admin.util.ServerInfo;
import com.zrlog.admin.util.ServerInfoUtils;
import com.zrlog.admin.util.SystemLoad;
import com.zrlog.admin.util.UploadFileUtils;
import com.zrlog.business.plugin.type.StaticSiteType;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminTypeAndUtilContractTest {

    @Test
    public void shouldExposeAdminAuditActionMetadata() {
        assertEquals("admin.audit.action.loginSuccess", AdminAuditAction.LOGIN_SUCCESS.getI18nKey());
        assertEquals("login", AdminAuditAction.LOGIN_SUCCESS.getType());
        assertEquals("article", AdminAuditAction.CREATE_ARTICLE.getType());
        assertEquals("system", AdminAuditAction.EXECUTE_UPGRADE.getType());
    }

    @Test
    public void shouldExposeFileActionEnums() {
        assertEquals(FileDirectoryAction.UPLOAD, FileDirectoryAction.valueOf("UPLOAD"));
        assertEquals(FileDirectoryAction.MKDIR, FileDirectoryAction.valueOf("MKDIR"));
        assertEquals(FileEntryAction.OPEN, FileEntryAction.valueOf("OPEN"));
        assertEquals(FileEntryAction.SELECT, FileEntryAction.valueOf("SELECT"));
        assertEquals(FileEntryAccess.PUBLIC_URL, FileEntryAccess.valueOf("PUBLIC_URL"));
    }

    @Test
    public void shouldExposeUploadAndServerInfoContracts() {
        UploadServiceResponseEntity response = new UploadServiceResponseEntity();
        response.setUrl("/upload/a.png");
        ServerInfo info = new ServerInfo("OS", "Linux", "os");

        assertEquals("/upload/a.png", response.getUrl());
        assertEquals("OS", info.getName());
        assertEquals("Linux", info.getValue());
        assertEquals("os", info.getKey());
    }

    @Test
    public void shouldConfigureAndDisableDevStaticMappings() {
        ServerConfig serverConfig = new ServerConfig();

        DevKit.configDev(serverConfig);

        assertTrue(serverConfig.getStaticResourceMapper().containsKey(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH + "/"));
        assertTrue(serverConfig.getStaticResourceMapper().containsKey(
                AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH + "/"));

        DevKit.disableDev(serverConfig);

        assertFalse(serverConfig.getStaticResourceMapper().containsKey(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH + "/"));
        assertFalse(serverConfig.getStaticResourceMapper().containsKey(
                AdminConstants.ADMIN_DEV_FILE_SYSTEM_TEMP_URI_BASE_PATH + "/"));
    }

    @Test
    public void shouldGenerateAttachedUrisFromFilesAndNames() throws Exception {
        File file = Files.createTempFile("zrlog-upload", ".PNG").toFile();
        Files.write(file.toPath(), "image".getBytes(StandardCharsets.UTF_8));

        String fromFile = UploadFileUtils.generatorUri("images", file);
        String fromAttachedDir = UploadFileUtils.generatorUri("/attached/custom", file);
        String fromAttachedRoot = UploadFileUtils.generatorUri("/attached", file);
        String fromNestedDir = UploadFileUtils.generatorUri("nested\\dir//child", file);
        String fromUnsafeDir = UploadFileUtils.generatorUri("../bad", file);
        String fromNullDir = UploadFileUtils.generatorUri(null, file);
        String fromName = UploadFileUtils.generatorUri("docs", "readme.md");

        assertTrue(fromFile.startsWith("/attached/images/"));
        assertTrue(fromFile.endsWith(".png"));
        assertTrue(fromAttachedDir.startsWith("/attached/custom/"));
        assertTrue(fromAttachedRoot.startsWith("/attached/"));
        assertFalse(fromAttachedRoot.matches("/attached/\\d{8}/.*"));
        assertTrue(fromNestedDir.startsWith("/attached/nested/dir/child/"));
        assertTrue(fromUnsafeDir.startsWith("/attached/"));
        assertTrue(fromUnsafeDir.matches("/attached/\\d{8}/.*\\.png"));
        assertTrue(fromNullDir.matches("/attached/\\d{8}/.*\\.png"));
        assertTrue(fromName.startsWith("/attached/docs/"));
        assertTrue(fromName.endsWith(".md"));
    }

    @Test
    public void shouldGenerateAttachedUriFromRequestUploadFallback() throws Exception {
        File file = Files.createTempFile("zrlog-upload-request", ".txt").toFile();
        Files.write(file.toPath(), "body".getBytes(StandardCharsets.UTF_8));

        String uri = UploadFileUtils.generatorUri("upload", requestWithFile(file));

        assertTrue(uri.startsWith("/attached/request-dir/"));
        assertTrue(uri.endsWith(".txt"));
        assertEquals("", UploadFileUtils.generatorUri("upload", requestWithFile(null)));
    }

    @Test
    public void shouldUseNamedUploadFieldBeforeDefaultFileFallback() throws Exception {
        File avatar = Files.createTempFile("zrlog-avatar", ".jpg").toFile();
        File fallback = Files.createTempFile("zrlog-fallback", ".txt").toFile();
        Files.write(avatar.toPath(), "avatar".getBytes(StandardCharsets.UTF_8));
        Files.write(fallback.toPath(), "fallback".getBytes(StandardCharsets.UTF_8));

        String uri = UploadFileUtils.generatorUri("avatar", requestWithFiles(Map.of(
                "avatar", avatar,
                "file", fallback
        )));

        assertTrue(uri.startsWith("/attached/request-dir/"));
        assertTrue(uri.endsWith(".jpg"));
    }

    @Test
    public void shouldRedirectUnLoginPageRequestsWithEncodedOriginalUri() {
        AtomicReference<String> redirect = new AtomicReference<>();

        AdminWebTools.blockUnLoginRequestHandler(request("/admin", "/blog", "a=1 b=2"), response(redirect));

        assertTrue(redirect.get().startsWith(AdminConstants.ADMIN_LOGIN_URI_PATH + "?redirectFrom="));
        assertTrue(redirect.get().contains("%2Fblog%2Fadmin%2Findex"));
        assertTrue(redirect.get().contains("a%3D1+b%3D2"));
    }

    @Test
    public void shouldReturnEmptyAdminStaticBaseWhenConfigIsMissing() {
        assertEquals("", AdminWebTools.getAdminStaticResourceBaseUrlByWebSite(request("/admin", "/blog", "")));
    }

    @Test
    public void shouldParseSystemLoadLines() throws Exception {
        Method method = SystemLoad.class.getDeclaredMethod("parseLine", String.class);
        method.setAccessible(true);

        assertEquals("1.23, 0.50, 0.10", method.invoke(null,
                "up 1 day, load averages: 1.23, 0.50, 0.10"));
        assertEquals("1.23, 0.50, 0.10", method.invoke(null,
                "load average: 1.23, 0.50, 0.10"));
        assertEquals("---", method.invoke(null, "no load here"));
    }

    @Test
    public void shouldBuildStaticSiteProgressMap() throws Exception {
        Method method = AdminStaticSiteProgress.class.getDeclaredMethod(
                "toMap", int.class, int.class, int.class, int.class, int.class, List.class);
        method.setAccessible(true);

        Map<?, ?> map = (Map<?, ?>) method.invoke(null, 4, 1, 1, 1, 1, List.of(StaticSiteType.BLOG.name()));

        assertEquals(4, map.get("total"));
        assertEquals(1, map.get("handled"));
        assertEquals(1, map.get("handing"));
        assertEquals(1, map.get("pending"));
        assertEquals(1, map.get("retrying"));
        assertEquals(List.of("BLOG"), map.get("siteTypes"));
    }

    @Test
    public void shouldConvertServerInfoMapAndFormatFileSizes() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("os.name", "Linux");
        data.put("os.arch", "amd64");
        data.put("os.version", "6.0");
        data.put("zrlog.runtime.path", "/app");
        data.put("java.vm.name", "VM");
        data.put("java.version", "21");
        data.put("server.info", "SWS/1");
        data.put("user.timezone", "Asia/Shanghai");
        data.put("dbServer.version", "MySQL");
        data.put("file.encoding", "UTF-8");

        List<ServerInfo> infos = ServerInfoUtils.convertToServerInfos(data);

        assertEquals(10, infos.size());
        assertEquals("system", infos.get(0).getKey());
        assertEquals("Linux - amd64 - 6.0", infos.get(0).getValue());
        assertEquals("runPath", infos.get(1).getKey());
        assertEquals("/app", infos.get(1).getValue());
        assertEquals("runtime", infos.get(2).getKey());
        assertEquals("VM - 21", infos.get(2).getValue());

        Method method = ServerInfoUtils.class.getDeclaredMethod("formatFileSize", long.class);
        method.setAccessible(true);
        assertEquals(".00B", method.invoke(null, 0L));
        assertEquals("1.00K", method.invoke(null, 1024L));
        assertEquals("1.00M", method.invoke(null, 1024L * 1024));
        assertEquals("1.00G", method.invoke(null, 1024L * 1024 * 1024));
    }

    private static HttpRequest requestWithFile(File file) {
        return requestWithFiles(file == null ? Map.of() : Map.of("file", file));
    }

    private static HttpRequest requestWithFiles(Map<String, File> files) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminTypeAndUtilContractTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getFile".equals(method.getName())) {
                        return files.get(args[0]);
                    }
                    if ("getParaToStr".equals(method.getName())) {
                        return "request-dir";
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpRequest request(String uri, String contextPath, String queryString) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminTypeAndUtilContractTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("getQueryStr".equals(method.getName())) {
                        return queryString;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpResponse response(AtomicReference<String> redirect) {
        return (HttpResponse) Proxy.newProxyInstance(
                AdminTypeAndUtilContractTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> {
                    if ("redirect".equals(method.getName())) {
                        redirect.set(args[0].toString());
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpResponseProxy";
                    }
                    return null;
                });
    }
}
