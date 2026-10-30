package com.zrlog.admin.web;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.controller.api.AdminDevController;
import com.zrlog.admin.web.controller.page.AdminPageController;
import com.zrlog.admin.web.interceptor.*;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.business.web.ServiceWebSetup;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.web.DataWebSetup;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.*;

public class AdminWebSetupProviderTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldExposeAdminWebSetupProviderMetadataAndFactory() throws Exception {
        PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());
        AdminWebSetupProvider provider = new AdminWebSetupProvider();
        ZrLogConfig config = new NotInstalledConfig();
        WebSetupContext context = new WebSetupContext(
                config,
                temporaryFolder.newFile("db.properties"),
                temporaryFolder.newFile("install.lock"),
                "/blog",
                null
        );

        WebSetup setup = provider.create(context);

        assertEquals("admin", provider.name());
        assertEquals(100, provider.order());
        assertTrue(setup instanceof AdminWebSetup);
    }

    @Test
    public void shouldConfigureAdminWebSetupRoutesInterceptorsAndPlugins() throws Exception {
        PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());
        NotInstalledConfig config = new NotInstalledConfig();
        new DataWebSetup(config).setup();
        new ServiceWebSetup(new WebSetupContext(config, null, null, "/blog", null)).setup();
        AdminWebSetup setup = new AdminWebSetup(config);

        setup.setup();
        assertFalse(config.getServerConfig().getInterceptors().contains(AdminPwaInterceptor.class));
        assertFalse(config.getServerConfig().getStaticResourceMapper().containsKey("/admin/static/"));
        AdminUiWebSetup ui = new AdminUiWebSetup(config, "/blog");
        ui.setup();
        assertEquals(AdminPageController.class.getMethod("index"),
                config.getServerConfig().getRouter().getMethod("/admin/dev/ui", HttpMethod.GET));
        assertEquals(AdminDevController.class.getMethod("ui"),
                config.getServerConfig().getRouter().getMethod("/api/admin/dev/ui", HttpMethod.GET));
        assertTrue(ui.getAdminResource().getAdminPageUris().contains("/blog/admin/dev/ui"));
        List<IPlugin> plugins = setup.getPlugins();
        plugins.addAll(ui.getPlugins());

        assertTrue(config.getServerConfig().getInterceptors().contains(AdminCrossOriginInterceptor.class));
        assertTrue(config.getServerConfig().getInterceptors().contains(AdminPwaInterceptor.class));
        assertTrue(config.getServerConfig().getInterceptors().contains(AdminStaticResourceInterceptor.class));
        assertTrue(config.getServerConfig().getInterceptors().contains(AdminLoginInterceptor.class));
        assertTrue(config.getServerConfig().getInterceptors().contains(AdminInterceptor.class));
        assertTrue(config.getServerConfig().getStaticResourceMapper().containsKey(
                AdminConstants.ADMIN_URI_BASE_PATH + "/static/"));
        assertTrue(config.getServerConfig().getStaticResourceMapper().containsKey(
                AdminConstants.ADMIN_URI_BASE_PATH + "/pwa/"));
        assertFalse(plugins.isEmpty());
        assertTrue(plugins.stream().anyMatch(UpdateVersionInfoPlugin.class::isInstance));
        assertTrue(plugins.stream().anyMatch(AdminStaticResourcePlugin.class::isInstance));
    }

    @Test
    public void realAssemblyCreatesSharedParentBeforeTransferringPluginScheduleOwnership() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            db.execute("UPDATE user SET role=? WHERE userId=?", "owner", 1);
            db.loadAdminWebModules();
            var component = db.component(com.zrlog.admin.web.di.automation.AutomationComponent.class);
            java.util.List<Boolean> ownership = new java.util.ArrayList<>();
            var core = (com.zrlog.business.plugin.PluginCorePlugin) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{com.zrlog.business.plugin.PluginCorePlugin.class}, (proxy, method, args) -> {
                        if (method.getName().equals("supportsHostScheduler")) return true;
                        if (method.getName().equals("setExternallyScheduled")) {
                            ownership.add((Boolean) args[0]);
                            if (ownership.size() == 1) assertEquals(5, component.store().taskCount(0));
                        }
                        return method.getReturnType() == boolean.class ? false : null;
                    });
            db.config().getAllPlugins().add(core);
            var setup = db.config().getWebSetup(AdminWebSetup.class);
            assertEquals(4, component.store().taskCount(0));
            assertEquals(0, component.store().taskCount(1));
            setup.getPlugins();
            assertEquals(List.of(true), ownership);
            var parent = component.store().tasks(0, 100, 0).stream()
                    .filter(row -> row.value.type.equals(com.zrlog.admin.business.automation.PluginScheduledTaskProvider.TYPE))
                    .findFirst().orElseThrow();
            var owner = com.zrlog.data.security.AccountAccess.from(new com.zrlog.model.User(db.dataSource()).loadById(1));
            assertThrows(IllegalArgumentException.class, () -> component.tasks().delete(owner, parent.value.id, parent.value.revision));
            component.tasks().setEnabled(owner, parent.value.id, parent.value.revision, false);
            setup.getPlugins();
            assertEquals(List.of(true, true), ownership);
            assertFalse(component.store().task(parent.value.id).value.enabled);
            assertEquals(5, component.store().taskCount(0));
        }
    }

    @Test
    public void assemblyKeepsPluginSchedulingWhenNoOwnerCanInitializeSharedTasks() throws Exception {
        try (var db = com.zrlog.admin.support.InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            java.util.List<Boolean> ownership = new java.util.ArrayList<>();
            var core = (com.zrlog.business.plugin.PluginCorePlugin) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[]{com.zrlog.business.plugin.PluginCorePlugin.class}, (proxy, method, args) -> {
                        if (method.getName().equals("supportsHostScheduler")) return true;
                        if (method.getName().equals("setExternallyScheduled")) ownership.add((Boolean) args[0]);
                        return method.getReturnType() == boolean.class ? false : null;
                    });
            db.config().getAllPlugins().add(core);
            db.config().getWebSetup(AdminWebSetup.class).getPlugins();
            assertEquals(List.of(false), ownership);
            assertEquals(0, db.component(com.zrlog.admin.web.di.automation.AutomationComponent.class).store().taskCount(1));
            assertEquals(0, db.component(com.zrlog.admin.web.di.automation.AutomationComponent.class).store().taskCount(0));
        }
    }

    private static class NotInstalledConfig extends ZrLogConfig {

        NotInstalledConfig() {
            super(18080, null, "");
        }

        @Override
        public boolean isInstalled() {
            return false;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

}
