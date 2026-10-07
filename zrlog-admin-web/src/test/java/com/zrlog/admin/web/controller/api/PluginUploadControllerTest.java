package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
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
    private static final String PATH = "/api/admin/plugins/upload";

    @Test
    public void bearerUploadRequiresPluginPermissionAndForwardsOriginalRequest() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            var config = Constants.zrLogConfig;
            config.getServerConfig().setContextPath("/sub");
            AdminRouters.configAdminRoute(config.getServerConfig().getRouter());
            assertNull(config.getServerConfig().getRouter().getMethod(PATH, HttpMethod.GET));
            AtomicInteger forwarded = new AtomicInteger();
            byte[] body = "multipart plugin bytes".getBytes(StandardCharsets.UTF_8);
            config.getAllPlugins().add((PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                        if (method.getName().equals("accessPlugin")) {
                            forwarded.incrementAndGet();
                            assertEquals("/api/upload", args[0]);
                            HttpRequest request = (HttpRequest) args[1];
                            assertArrayEquals(body, request.getInputStream().readAllBytes());
                            assertEquals("fileName=travel.jar&overwrite=true", request.getQueryStr());
                            assertEquals(1, ((AdminTokenVO) args[3]).getUserId());
                            ((HttpResponse) args[2]).renderCode(200);
                            return true;
                        }
                        return false;
                    }));
            String allowed = token("plugin.manage");
            String denied = token("site.configure");
            AdminTokenThreadLocal.remove();
            assertEquals(403, call(denied, body));
            assertEquals(401, call("invalid-token", body));
            assertEquals(0, forwarded.get());
            assertEquals(200, call(allowed, body));
            assertEquals(1, forwarded.get());
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    private String token(String permission) throws Exception {
        PersonalTokenModels.Create create = new PersonalTokenModels.Create();
        create.name = permission;
        create.permissionMode = "custom";
        create.permissions = List.of(permission);
        return new PersonalAccessTokenService(new OAuthService().mcpResource()).create(create).token;
    }

    private int call(String token, byte[] body) throws Exception {
        var config = Constants.zrLogConfig;
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUri": return PATH;
                        case "getMethod": return HttpMethod.POST;
                        case "getContextPath": return "/sub";
                        case "getHeader": return "Authorization".equals(args[0]) ? "Bearer " + token
                                : "Host".equals(args[0]) ? "localhost:18080" : null;
                        case "getHeaderMap": case "getParamMap": case "decodeParamMap": return Map.of();
                        case "getInputStream": return new ByteArrayInputStream(body);
                        case "getQueryStr": return "fileName=travel.jar&overwrite=true";
                        case "getRemoteHost": return "127.0.0.1";
                        case "getServerConfig": return config.getServerConfig();
                        case "getRequestConfig": return config.getRequestConfig();
                        default: return null;
                    }
                });
        AtomicInteger status = new AtomicInteger(200);
        HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                    if (method.getName().equals("renderCode")) status.set((Integer) args[0]);
                    if (method.getName().equals("write") && args.length > 1 && args[1] instanceof Integer) status.set((Integer) args[1]);
                    return null;
                });
        new BearerTokenInterceptor().doInterceptor(request, response);
        return status.get();
    }
}
