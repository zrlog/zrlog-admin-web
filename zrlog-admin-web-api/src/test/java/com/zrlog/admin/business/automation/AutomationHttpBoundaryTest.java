package com.zrlog.admin.business.automation;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.di.automation.AutomationComponent;
import com.zrlog.admin.web.interceptor.AdminInterceptor;
import com.zrlog.admin.web.interceptor.AutomationTickInterceptor;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.User;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AutomationHttpBoundaryTest {
    @Test
    public void actualAssemblyRegistersTriggerBeforeBrowserAuthAndManagementRequiresSession() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var config = db.config().getServerConfig();
            var interceptors = config.getInterceptors();
            assertTrue(interceptors.indexOf(AutomationTickInterceptor.class) >= 0);
            assertTrue(interceptors.indexOf(AutomationTickInterceptor.class) < interceptors.indexOf(AdminInterceptor.class));
            var accounts = db.component(AccountComponent.class);
            var tasks = db.component(AutomationComponent.class);
            var configurationRoute = config.getRouter().getMethod("/api/admin/website/assistant", HttpMethod.GET);
            assertNotNull(configurationRoute);
            assertNull(config.getRouter().getMethod("/api/admin/website/assistant", HttpMethod.POST));
            var configuration = tasks.controller().configuration().getData();
            assertTrue(configuration.canManageScheduler);
            assertTrue(configuration.canConfigureAi);
            assertNotNull(config.getRouter().getMethod("/api/admin/assistant", HttpMethod.GET));
            assertNotNull(config.getRouter().getMethod("/api/admin/assistant/notices", HttpMethod.GET));
            assertNotNull(config.getRouter().getMethod("/api/admin/assistant/readNotice", HttpMethod.POST));
            assertNull(config.getRouter().getMethod("/api/admin/assistant/readNotice", HttpMethod.GET));
            assertNotNull(config.getRouter().getMethod("/api/admin/assistant/save", HttpMethod.POST));
            var proposalMethod = config.getRouter().getMethod("/api/admin/assistant/propose", HttpMethod.POST);
            assertNotNull(proposalMethod);
            assertNull(config.getRouter().getMethod("/api/admin/assistant/propose", HttpMethod.GET));
            assertEquals(com.zrlog.data.security.AccountAction.ARTICLE_ASSIST,
                    com.zrlog.admin.web.controller.api.AutomationController.class.getMethod("propose")
                            .getAnnotation(com.zrlog.admin.web.annotation.RequiresAction.class).value());
            assertNull(config.getRouter().getMethod("/api/admin/assistant/save", HttpMethod.GET));
            assertThrows(PermissionErrorException.class, () -> tasks.tasks().tasks(null, 0));
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            assertTrue(tasks.tasks().definitions(actor).stream().anyMatch(item -> item.type.equals("assistant.reminder")));
            db.execute("insert into user(userId,userName,role) values(?,?,?)", 2, "author", "author");
            AccountAccess author = AccountAccess.from(new User(db.dataSource()).loadById(2));
            assertThrows(PermissionErrorException.class, () -> tasks.settings().read(author));
            assertTrue(tasks.tasks().definitions(author).stream().noneMatch(item -> item.requiredActions.contains("system.manage")));
            db.execute("UPDATE user SET role=? WHERE userId=?", "author", 1);
            assertThrows(PermissionErrorException.class, () -> tasks.controller().configuration());
        }
    }

    @Test
    public void unifiedSettingsKeepExecutionAndAiPermissionsSeparate() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var tasks = db.component(AutomationComponent.class);
            for (AccountAction action : new AccountAction[]{AccountAction.SYSTEM_MANAGE, AccountAction.SITE_CONFIGURE}) {
                DelegatedAccess.withPermissions(Set.of(AccountAction.SESSION.getId(), action.getId()), false, () -> {
                    var configuration = tasks.controller().configuration().getData();
                    assertEquals(action == AccountAction.SYSTEM_MANAGE, configuration.canManageScheduler);
                    assertEquals(action == AccountAction.SITE_CONFIGURE, configuration.canConfigureAi);
                    var actor = AccountAccess.from(new User(db.dataSource()).loadById(1))
                            .restrictActions(Set.of(action.getId()));
                    if (action == AccountAction.SITE_CONFIGURE) {
                        assertThrows(PermissionErrorException.class, () -> tasks.settings().read(actor));
                    } else {
                        assertTrue(tasks.settings().read(actor).internalEnabled);
                    }
                    return null;
                });
            }
            DelegatedAccess.withPermissions(Set.of(AccountAction.SESSION.getId()), false, () -> {
                assertThrows(PermissionErrorException.class, () -> tasks.controller().configuration());
                return null;
            });
        }
    }

    @Test
    public void bearerEndpointIsExactMethodBoundedRevocableAndCannotMutateTasks() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            db.loadAdminWebModules();
            var component = db.component(AutomationComponent.class);
            AccountAccess actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var issued = component.settings().rotate(actor, 0);
            assertEquals("/api/admin/assistant/trigger", issued.settings.triggerPath);
            assertEquals(AutomationTickInterceptor.PATH, issued.settings.triggerPath);
            var interceptor = new AutomationTickInterceptor(component.settings(), component.ticks());
            AtomicInteger code = new AtomicInteger(200);
            AtomicReference<Object> json = new AtomicReference<>();
            Map<String, String> headers = new HashMap<>();
            HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                if (method.getName().equals("renderCode")) code.set((Integer) args[0]);
                if (method.getName().equals("renderJson")) json.set(args[0]);
                if (method.getName().equals("addHeader")) headers.put(args[0].toString(), args[1].toString());
                return null;
            });
            assertTrue(interceptor.isHandleAble(request(HttpMethod.POST, AutomationTickInterceptor.PATH, issued.token)));
            assertFalse(interceptor.isHandleAble(request(HttpMethod.POST, "/api/admin/scheduler/tick", issued.token)));
            assertFalse(interceptor.isHandleAble(request(HttpMethod.POST, AutomationTickInterceptor.PATH + "/save", issued.token)));
            assertFalse(interceptor.isHandleAble(request(HttpMethod.POST, "/api/admin/assistant/save", issued.token)));
            interceptor.doInterceptor(request(HttpMethod.GET, AutomationTickInterceptor.PATH, issued.token), response);
            assertEquals(405, code.get());
            assertEquals("POST", headers.get("Allow"));
            interceptor.doInterceptor(request(HttpMethod.POST, AutomationTickInterceptor.PATH, null), response);
            assertEquals(401, code.get());
            code.set(200);
            interceptor.doInterceptor(request(HttpMethod.POST, AutomationTickInterceptor.PATH, issued.token), response);
            assertEquals(200, code.get());
            assertEquals("no-store", headers.get("Cache-Control"));
            assertTrue(json.get() instanceof ApiStandardResponse);
            assertEquals(0, component.tasks().tasks(actor, 0).size());
            assertNotNull(component.ticks().health("external"));
            component.settings().revoke(actor, issued.settings.revision);
            interceptor.doInterceptor(request(HttpMethod.POST, AutomationTickInterceptor.PATH, issued.token), response);
            assertEquals(401, code.get());
        }
    }

    private HttpRequest request(HttpMethod verb, String path, String token) {
        return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getMethod": return verb;
                case "getUri": return path; // SWS strips the context path before interceptor matching.
                case "getContextPath": return "/sub";
                case "getHeader": return "Authorization".equals(args[0]) && token != null ? "Bearer " + token : null;
                case "getInputStream": throw new AssertionError("Trigger must not read task configuration from its body");
                default: return null;
            }
        });
    }
}
