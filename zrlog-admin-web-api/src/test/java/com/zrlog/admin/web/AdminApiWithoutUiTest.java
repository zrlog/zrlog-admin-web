package com.zrlog.admin.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class AdminApiWithoutUiTest {
    @Test
    public void apiAndAccountInformationWorkWithoutUiOnTheClasspath() throws Exception {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.zrlog.admin.web.AdminUiWebSetup"));
        assertNull(getClass().getResource("/admin/index.html"));
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var router = db.config().getServerConfig().getRouter();
            assertNull(db.config().getWebSetup(com.zrlog.admin.business.service.AdminResourceProvider.class));
            assertNull(router.getMethod("/admin", HttpMethod.GET));
            assertNull(router.getMethod("/api/admin/manifest", HttpMethod.GET));
            assertNotNull(router.getMethod("/api/admin/login", HttpMethod.POST));
            assertNotNull(router.getMethod("/api/admin/article", HttpMethod.GET));
            var profile = db.config().getServerConfig().getBindings().build()
                    .get(AccountComponent.class).profiles().getBasicUserInfo(1, "api-only");
            assertTrue(profile.getCacheableApiUris().isEmpty());
            assertTrue(profile.getPasskeys().isEmpty());
            assertEquals("admin", profile.getUserName());
            HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getUri")) return "/api/public/adminResource";
                        if (method.getName().equals("getContextPath")) return "";
                        if (method.getName().equals("getRemoteHost")) return "127.0.0.1";
                        if (method.getName().equals("getScheme")) return "http";
                        if (method.getName().equals("getHeader")) return "Host".equals(args[0]) ? "localhost:18080" : null;
                        return null;
                    });
            var capabilities = db.config().getServerConfig().getBindings().build().get(com.zrlog.admin.business.service.AdminResourceService.class).adminResourceInfo(request).getCapabilities();
            assertTrue(capabilities.account);
            assertTrue(capabilities.content);
            assertTrue(capabilities.ai);
            assertTrue(capabilities.mcp);
        }
    }
}
