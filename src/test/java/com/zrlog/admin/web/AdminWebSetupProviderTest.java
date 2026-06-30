package com.zrlog.admin.web;

import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.web.interceptor.AdminCrossOriginInterceptor;
import com.zrlog.admin.web.interceptor.AdminInterceptor;
import com.zrlog.admin.web.interceptor.AdminLoginInterceptor;
import com.zrlog.admin.web.interceptor.AdminPwaInterceptor;
import com.zrlog.admin.web.interceptor.AdminStaticResourceInterceptor;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        AdminWebSetup setup = new AdminWebSetup(config, new FakeAdminResource(), "/blog");

        setup.setup();
        List<IPlugin> plugins = setup.getPlugins();

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

    private static class FakeAdminResource implements AdminResource {

        @Override
        public Set<String> getAdminStaticResourceUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminPageUris() {
            return Set.of("/blog/admin/index");
        }

        @Override
        public Set<String> getAdminStaticCacheUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminCacheableApiUris() {
            return Set.of();
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return null;
        }

        @Override
        public String getStaticResourceBuildId() {
            return "test-build";
        }

        @Override
        public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
            return new AdminResourceInfoResponse();
        }
    }
}
