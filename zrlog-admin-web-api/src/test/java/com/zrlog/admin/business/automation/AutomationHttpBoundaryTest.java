package com.zrlog.admin.business.automation;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.impl.HttpRequestDecoderImpl;
import com.hibegin.http.server.web.Controller;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AutomationHttpBoundaryTest {
    @Test
    public void authenticatedTriggerFinishesAnInstalledUpgradeThroughTheProductionAssembly() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.openWebApi()) {
            db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
            db.loadAdminWebModules();
            var component = db.component(AutomationComponent.class);
            component.defaults().initialize();
            var store = component.store();
            var actor = AccountAccess.from(new User(db.dataSource()).loadById(1));
            var task = store.task(AutomationDefaults.systemId("system.auto-upgrade"));
            var run = new AutomationModels.Run();
            run.id = AutomationStore.ownerPrefix(0) + AutomationStore.timeKey(System.currentTimeMillis()) + "." + UUID.randomUUID();
            run.taskId = task.value.id;
            run.taskType = task.value.type;
            run.startedAt = System.currentTimeMillis();
            run.scheduledAt = run.startedAt;
            run.trigger = "external";
            run.status = "running";
            store.insertRun(run);
            var active = task.copy(StoredTask.class);
            active.activeRun = run;
            active.leaseUntil = run.startedAt + 660000;
            assertTrue(store.replaceTask(task, active, actor));
            var receipt = new AutomaticUpgradeTaskProvider.Receipt();
            receipt.source = "assistant";
            receipt.state = "pending";
            receipt.runId = run.id;
            receipt.buildId = com.zrlog.util.BlogBuildInfoUtil.getBuildId();
            assertTrue(store.writeAuthorized(AutomaticUpgradeTaskProvider.KEY, null, receipt, actor));
            var issued = component.settings().rotate(actor, 0);
            var result = new AtomicReference<Object>();
            HttpResponse response = (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                        if (method.getName().equals("renderJson")) result.set(args[0]);
                        if (method.getName().equals("renderCode")) fail("Trigger rejected: " + args[0]);
                        return null;
                    });
            new AutomationTickInterceptor(component.settings(), component.ticks()).doInterceptor(
                    request(HttpMethod.POST, AutomationTickInterceptor.PATH, issued.token), response);
            assertTrue(result.get() instanceof ApiStandardResponse);
            assertEquals("success", store.run(run.id).value.status);
            assertEquals("success", store.run(run.id).value.result.children.get(1).status);
            assertNull(store.task(run.taskId).value.activeRun);
            assertFalse(store.task(run.taskId).value.enabled);
            assertEquals("confirmed", store.read(AutomaticUpgradeTaskProvider.KEY, AutomaticUpgradeTaskProvider.Receipt.class).value.state);
        }
    }

    @Test
    public void historyDefaultsToTwentyRowsAndPagesWithoutRepeatingOrExposingOtherAccounts() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var store = db.component(AutomationComponent.class).store();
            String taskId = AutomationStore.ownerPrefix(1) + UUID.randomUUID();
            long now = System.currentTimeMillis();
            for (int i = 0; i < 28; i++) {
                var run = new AutomationModels.Run();
                run.ownerId = i < 25 ? 1 : 2;
                run.id = AutomationStore.ownerPrefix(run.ownerId) + AutomationStore.timeKey(now + i) + "." + UUID.randomUUID();
                run.taskId = run.ownerId == 1 ? taskId : AutomationStore.ownerPrefix(2) + UUID.randomUUID();
                run.taskName = "History " + i;
                run.status = "success";
                run.startedAt = now + i;
                store.insertRun(run);
            }
            var first = history(db, "?page=1&taskId=");
            assertEquals(20, first.pageSize);
            assertEquals(25, first.total);
            assertEquals(20, first.items.size());
            assertEquals("History 24", first.items.get(0).taskName);
            assertEquals("History 5", first.items.get(19).taskName);
            var second = history(db, "?page=2&taskId=");
            assertEquals(2, second.page);
            assertEquals(20, second.pageSize);
            assertEquals(25, second.total);
            assertEquals(5, second.items.size());
            assertEquals("History 4", second.items.get(0).taskName);
            assertEquals("History 0", second.items.get(4).taskName);
            assertEquals(20, history(db, "?taskId=" + taskId).items.size());
            assertEquals(1, history(db, "?pageSize=1").items.size());
            assertEquals(100, history(db, "?pageSize=999").pageSize);
            assertThrows(NumberFormatException.class, () -> history(db, "?pageSize=invalid"));
        }
    }

    private AutomationViews.Runs history(InMemoryZrLogDatabase db, String query) throws Exception {
        var config = db.config().getServerConfig();
        var decoder = new HttpRequestDecoderImpl(new RequestConfig(), new ApplicationContext(config), null);
        assertTrue(decoder.doDecode(ByteBuffer.wrap(("GET /api/admin/assistant/runs" + query
                + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.UTF_8))).getKey());
        var request = decoder.getRequest();
        var route = config.getRouter().getMethod(request.getUri(), HttpMethod.GET);
        var controller = (com.zrlog.admin.web.controller.api.AutomationController)
                Controller.buildController(route, request, null);
        return controller.runs().getData();
    }

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
            assertTrue(configuration.settings.internalEnabled);
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
    public void assistantSettingsRequireSystemManagementIndependentlyOfAiConfiguration() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var tasks = db.component(AutomationComponent.class);
            assertEquals(AccountAction.SYSTEM_MANAGE,
                    com.zrlog.admin.web.controller.api.AutomationController.class.getMethod("configuration")
                            .getAnnotation(com.zrlog.admin.web.annotation.RequiresAction.class).value());
            DelegatedAccess.withPermissions(Set.of(AccountAction.SESSION.getId(), AccountAction.SYSTEM_MANAGE.getId()), false, () -> {
                assertTrue(tasks.controller().configuration().getData().settings.internalEnabled);
                return null;
            });
            for (var actions : java.util.List.of(Set.of(AccountAction.SESSION.getId(), AccountAction.SITE_CONFIGURE.getId()),
                    Set.of(AccountAction.SESSION.getId()))) {
                DelegatedAccess.withPermissions(actions, false, () -> {
                    assertThrows(PermissionErrorException.class, () -> tasks.controller().configuration());
                    return null;
                });
            }
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
