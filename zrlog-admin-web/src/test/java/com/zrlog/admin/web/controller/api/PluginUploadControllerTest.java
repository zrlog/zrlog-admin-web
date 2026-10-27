package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.UploadedFile;
import java.nio.file.Files;
import java.net.URLDecoder;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.McpWebSetup;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PluginUploadControllerTest {

    private String path = "/api/admin/plugins/install";
    private ZrLogConfig applicationConfig;
    private UploadedFile uploadedFile;
    private DataSourceWrapper testDataSource;
    private HttpMethod httpMethod = HttpMethod.POST;
    private String resourcePath;
    private byte[] received;

    @Test
    public void bearerUploadRequiresPluginPermissionAndForwardsOriginalRequest() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            testDataSource = ignored.dataSource();
            var config = ignored.config();
            config.getServerConfig().setContextPath("/sub");
            ignored.loadAdminWebModules();
            assertNull(config.getServerConfig().getRouter().getMethod(path, HttpMethod.GET));
            AtomicInteger forwarded = new AtomicInteger();
            byte[] body = "source=%2Fadmin%2Fattached%2Ftmp%2Fplugin.jar".getBytes(StandardCharsets.UTF_8);
            config.getAllPlugins().add((PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                        if (method.getName().equals("accessPlugin")) {
                            forwarded.incrementAndGet();
                            assertEquals("/api/install", args[0]);
                            HttpRequest request = (HttpRequest) args[1];
                            if (uploadedFile == null) {
                                assertArrayEquals(body, request.getInputStream().readAllBytes());
                                assertEquals("fileName=travel.jar&overwrite=true", request.getQueryStr());
                            } else {
                                String form = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                                assertTrue(form, form.startsWith("source=%2Fadmin%2Fattached%2Ftmp%2Fplugins%2F"));
                                resourcePath = URLDecoder.decode(form.substring("source=".length(), form.indexOf("&")), StandardCharsets.UTF_8);
                                assertTrue(form, form.contains("fileName=travel.jar&overwrite=true"));
                                assertEquals("", request.getQueryStr());
                                assertEquals("application/x-www-form-urlencoded;charset=UTF-8", request.getHeader("Content-Type"));
                            }
                            assertEquals(1, ((AdminTokenVO) args[3]).getUserId());
                            ((HttpResponse) args[2]).renderCode(200);
                            return true;
                        }
                        return false;
                    }));
            String allowed = token("plugin.manage");
            String denied = token("site.configure");
            String uploader = token("plugin.manage", "asset.upload");
            String reader = token("asset.upload");
            AdminTokenThreadLocal.remove();
            assertEquals(403, call(denied, body, ignored.dataSource()));
            assertEquals(401, call("invalid-token", body, ignored.dataSource()));
            assertEquals(0, forwarded.get());
            assertEquals(200, call(allowed, body, ignored.dataSource()));
            assertEquals(1, forwarded.get());
            assertNull(AdminTokenThreadLocal.getUser());
            path = "/api/admin/plugins/upload";
            var file = Files.createTempFile("plugin-upload-", ".jar");
            try {
                Files.write(file, new byte[]{1, 2, 3});
                uploadedFile = new UploadedFile("file", "travel.jar", "application/java-archive", file.toFile());
                assertEquals(200, call(uploader, body, ignored.dataSource()));
                assertEquals(2, forwarded.get());
                path = "/api/admin/upload/resource";
                httpMethod = HttpMethod.GET;
                assertEquals(403, call(allowed, new byte[0], ignored.dataSource()));
                assertEquals(200, call(reader, new byte[0], ignored.dataSource()));
                assertArrayEquals(new byte[]{1, 2, 3}, received);
                assertEquals(2, forwarded.get());
                resourcePath = "/admin/attached/tmp/plugins/missing.jar";
                assertEquals(404, call(reader, new byte[0], ignored.dataSource()));
            } finally {Files.deleteIfExists(file); uploadedFile = null;}
        }
    }

    private String token(String... permissions) throws Exception {
        PersonalTokenModels.Create create = new PersonalTokenModels.Create();
        create.name = String.join(",", permissions);
        create.permissionMode = "custom";
        create.permissions = List.of(permissions);
        return new PersonalAccessTokenService(
                new OAuthService(
                        ApplicationServicesFixture.backend(applicationConfig)
                                ::configured,
                        ApplicationServicesFixture.available(
                                applicationConfig, McpWebSetup.class),
                        AccountServicesFixture.component(testDataSource).permissions(), AccountServicesFixture.component(testDataSource).identities(),
                        AccountServicesFixture.component(testDataSource).security(),
                        tokenResource ->
                                new PersonalAccessTokenService(
                                        tokenResource,
                                        AccountServicesFixture.component(testDataSource)
                                                .identities(), AccountServicesFixture.component(testDataSource)
                                        .permissions(),
                                        AccountServicesFixture.component(testDataSource)
                                                .security()),
                        AccountServicesFixture.component(testDataSource).backend())
                        .mcpResource(),
                AccountServicesFixture.component(testDataSource).identities(), AccountServicesFixture.component(testDataSource).permissions(),
                AccountServicesFixture.component(testDataSource).security())
                .create(new GrantAuthority(AccountServicesFixture.component(testDataSource).requestIdentity().current(), !com.zrlog.admin.business.security.DelegatedAccess.restricted()), create)
                .token;
    }

    private int call(String token, byte[] body, DataSourceWrapper testDataSource) throws Exception {
        var config = applicationConfig;
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUploadedFile":
                            return "file".equals(args[0]) ? uploadedFile : null;
                        case "getParaToStr":
                            if ("path".equals(args[0])) return resourcePath;
                            if ("fileName".equals(args[0])) return "travel.jar";
                            if ("overwrite".equals(args[0])) return "true";
                            return args.length > 1 ? args[1] : null;
                        case "getUri":
                            return path;
                        case "getMethod":
                            return httpMethod;
                        case "getContextPath":
                            return "/sub";
                        case "getHeader":
                            return "Authorization".equals(args[0]) ? "Bearer " + token
                                    : "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap":
                        case "getParamMap":
                        case "decodeParamMap":
                            return Map.of();
                        case "getInputStream":
                            return new ByteArrayInputStream(body);
                        case "getQueryStr":
                            return "fileName=travel.jar&overwrite=true";
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getServerConfig":
                            return config.getServerConfig();
                        case "getRequestConfig":
                            return config.getRequestConfig();
                        default:
                            return null;
                    }
                });
        AtomicInteger status = new AtomicInteger(200);
        HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                    if (method.getName().equals("write") && args[0] instanceof java.io.InputStream) received = ((java.io.InputStream) args[0]).readAllBytes();
                    if (method.getName().equals("renderCode")) status.set((Integer) args[0]);
                    if (method.getName().equals("write") && args.length > 1 && args[1] instanceof Integer)
                        status.set((Integer) args[1]);
                    return null;
                });
        new BearerTokenInterceptor(
                () -> ApplicationServicesFixture.dispatcher(null, applicationConfig),
                new OAuthService(
                        ApplicationServicesFixture.backend(applicationConfig)::configured,
                        ApplicationServicesFixture.available(
                                applicationConfig, McpWebSetup.class),
                        AccountServicesFixture.component(testDataSource).permissions(), AccountServicesFixture.component(testDataSource).identities(),
                        AccountServicesFixture.component(testDataSource).security(),
                        tokenResource ->
                                new PersonalAccessTokenService(
                                        tokenResource,
                                        AccountServicesFixture.component(testDataSource).identities(), AccountServicesFixture.component(testDataSource).permissions(),
                                        AccountServicesFixture.component(testDataSource).security()),
                        AccountServicesFixture.component(testDataSource).backend()),
                AccountServicesFixture.component(testDataSource).routeAuthorizer(), AccountServicesFixture.component(testDataSource).requestIdentity(),
                AccountServicesFixture.component(testDataSource).preferences(),
                new WebhookService(
                        ApplicationServicesFixture.messages(testDataSource),
                        ApplicationServicesFixture.websiteCache(testDataSource)))
                .doInterceptor(request, response);
        return status.get();
    }
}
