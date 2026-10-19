package com.zrlog.admin.web.controller.api;

import static org.junit.Assert.*;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminProxyService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.config.AdminRouters;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.NetworkProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminProxyControllerTest {

    private ZrLogConfig applicationConfig;
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private String previousRoot;

    @Before public void isolateConfiguration() throws Exception {
        previousRoot = System.getProperty("sws.root.path");
        PathUtil.setRootPath(temporary.newFolder("runtime").toString());
    }

    @After public void restoreConfiguration() {
        if (previousRoot == null) System.clearProperty("sws.root.path");
        else System.setProperty("sws.root.path", previousRoot);
    }

    @Test
    public void bothProxyRoutesRequireSystemManagement() throws Exception {
        Router router = new Router();
        AdminRouters.configAdminRoute(router);
        for (String path : new String[]{"/api/admin/system/proxy", "/api/admin/system/proxy/test"}) {
            for (HttpMethod verb : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST}) {
                var method = router.getMethod(path, verb);
                assertNotNull(path, method);
                assertEquals(AdminProxyController.class, method.getDeclaringClass());
                assertEquals(AccountAction.SYSTEM_MANAGE, method.getAnnotation(RequiresAction.class).value());
                assertThrows(
                        PermissionErrorException.class,
                        () ->
                                AccountServicesFixture.component()
                                        .permissions()
                                        .checkRoute(method, null));
            }
        }
    }

    @Test
    public void unsupportedVerbsDoNotReadOrWriteConfiguration() throws Exception {
        AtomicInteger status = new AtomicInteger();
        AdminProxyController controller =
                new AdminProxyController(
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(),
                                AccountServicesFixture.component().permissions()),
                        new AdminProxyService());
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class},
                (proxy, method, args) -> method.getName().equals("getMethod") ? HttpMethod.DELETE : null);
        HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpResponse.class},
                (proxy, method, args) -> { if (method.getName().equals("renderCode")) status.set((Integer) args[0]); return null; });
        set(controller, "request", request);
        set(controller, "response", response);
        assertNull(controller.index());
        assertEquals(405, status.get());
        assertNull(controller.test());
        assertEquals(405, status.get());
    }

    @Test
    public void savesLocalFileAndRejectsInvalidInputWithoutLosingSavedSettings() throws Exception {
        try (InMemoryZrLogDatabase database = InMemoryZrLogDatabase.open()) {
            applicationConfig = database.config();
            database.loadAdminWebModules();
            NetworkProxy.Settings active = NetworkProxy.initialize();
            String proxyUrl = active.getUrl().equals("http://proxy.example:3128")
                    ? "http://proxy.example:3129" : "http://proxy.example:3128";
            AdminProxyController controller =
                    new AdminProxyController(
                            new AdminPageDataFactory(
                                    ApplicationServicesFixture.site(database.config()),
                                    ApplicationServicesFixture.buildId(database.resources()),
                                    ApplicationServicesFixture.messages(),
                                    AccountServicesFixture.component().permissions()),
                            new AdminProxyService());
            set(controller, "request", request(HttpMethod.POST,
                    "{\"url\":\"" + proxyUrl + "\",\"noProxy\":\".internal.example\"}"));
            var saved = controller.index().getData();
            assertEquals(proxyUrl, saved.getUrl());
            // Environment overrides may make a file edit ineffective. Saving must never apply it live.
            assertSame(active, NetworkProxy.initialize());
            assertEquals(active.getUrl(), saved.getActiveUrl());
            byte[] previousFile = Files.readAllBytes(NetworkProxy.configFile());
            assertTrue(new String(previousFile, StandardCharsets.ISO_8859_1).contains("network.proxy.url"));
            set(controller, "request", request(HttpMethod.POST,
                    "{\"url\":\"http://user:secret@proxy.example\",\"noProxy\":\"\"}"));
            assertThrows(ArgsException.class, controller::index);
            assertArrayEquals(previousFile, Files.readAllBytes(NetworkProxy.configFile()));
            set(controller, "request", request(HttpMethod.GET, ""));
            assertEquals(saved.getUrl(), controller.index().getData().getUrl());
        }
    }

    private HttpRequest request(HttpMethod verb, String body) {
        return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMethod")) return verb;
                    if (method.getName().equals("getUri")) return "/api/admin/system/proxy";
                    if (method.getName().equals("getInputStream")) return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    return null;
                });
    }

    private static void set(Controller controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
