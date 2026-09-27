package com.zrlog.admin.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResourceService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
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
            var router = Constants.zrLogConfig.getServerConfig().getRouter();
            assertNull(AdminConstants.adminResource);
            assertNull(router.getMethod("/admin", HttpMethod.GET));
            assertNull(router.getMethod("/api/admin/manifest", HttpMethod.GET));
            assertNotNull(router.getMethod("/api/admin/login", HttpMethod.POST));
            assertNotNull(router.getMethod("/api/admin/article", HttpMethod.GET));
            assertTrue(new UserService().getBasicUserInfo(1, "api-only").getCacheableApiUris().isEmpty());
            HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{HttpRequest.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getUri")) return "/api/public/adminResource";
                        if (method.getName().equals("getContextPath")) return "";
                        if (method.getName().equals("getRemoteHost")) return "127.0.0.1";
                        if (method.getName().equals("getScheme")) return "http";
                        if (method.getName().equals("getHeader")) return "Host".equals(args[0]) ? "localhost:18080" : null;
                        return null;
                    });
            var capabilities = new AdminResourceService().adminResourceInfo(request).getCapabilities();
            assertTrue(capabilities.account);
            assertTrue(capabilities.content);
            assertTrue(capabilities.ai);
            assertTrue(capabilities.mcp);
        }
    }
}
