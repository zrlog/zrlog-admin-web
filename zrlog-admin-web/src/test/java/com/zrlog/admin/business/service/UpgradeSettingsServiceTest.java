package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.test.support.DockerControlStub;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.WebSite;
import com.zrlog.updater.spi.DockerControl;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class UpgradeSettingsServiceTest {

    private static final String HUB = "docker.io/zrlog/zrlog";
    private static final String ALIYUN = "registry.cn-chengdu.aliyuncs.com/hibegin/zrlog";
    private ZrLogConfig applicationConfig;

    private static UpgradeSettingsRequest input(String repository) {
        UpgradeSettingsRequest input = new UpgradeSettingsRequest();
        input.setAutoUpgradeVersion(-1);
        input.setUpgradePreview(true);
        input.setDockerRepository(repository);
        return input;
    }

    private static Object value(InMemoryZrLogDatabase db, String key) throws SQLException {
        return db.scalar("select value from website where name = ?", key);
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> method.getName().equals("getUri") ? "/api/admin/website/upgrade" : null);
    }

    @Test
    public void explicitActorOwnsPackageSettingsAndDockerDelegationWithoutThreadIdentity() throws Exception {
        for (boolean sqlite : List.of(false, true)) {
            try (var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open()) {
                applicationConfig = db.config();
                plugin(db.dataSource());
                db.execute("insert into user(userId,userName,role) values(2,'operator','admin')");
                var credentials = new AdminTokenVO();
                credentials.setUserId(2);
                var actor = db.component(AccountComponent.class).identities().account(credentials);
                var graph = db.component(AdminComponent.class);
                var packages = graph.upgradeSettings();
                var siteOnly = actor.restrictActions(Set.of(AccountAction.SITE_CONFIGURE.getId()));
                var audit = new AdminAuditContext(2, "127.0.0.2", "/api/admin/website/upgrade", "Test worker");
                assertEquals(1, AdminTokenThreadLocal.getUserId());
                packages.save(siteOnly, input(null), audit);
                AdminTokenThreadLocal.remove();
                assertEquals(Integer.valueOf(-1), packages.read(siteOnly, null).getAutoUpgradeVersion());
                var logs = new Gson().fromJson(value(db, "admin_audit_log").toString(),
                        com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse[].class);
                assertEquals(Integer.valueOf(2), logs[0].getActorUserId());
                Client client = new Client();
                var containers = service(client, true, graph.settings(), db.dataSource());
                assertThrows(PermissionErrorException.class, () -> containers.read(siteOnly, null));
                assertThrows(PermissionErrorException.class, () -> containers.save(siteOnly, input(ALIYUN), audit));
                assertEquals(0, client.reads);
                assertTrue(client.saves.isEmpty());
                containers.save(actor, input(ALIYUN), audit);
                assertEquals(ALIYUN, client.repository);
                db.execute("update user set enabled=false where userId=2");
                assertThrows(PermissionErrorException.class, () -> packages.read(actor, null));
                assertThrows(PermissionErrorException.class, () -> packages.save(actor, input(null), audit));
                assertThrows(PermissionErrorException.class, () -> containers.save(actor, input(HUB), audit));
                assertEquals(List.of(ALIYUN + ":preview"), client.saves);
                assertNull(AdminTokenThreadLocal.getUser());
            }
        }
    }

    @Test
    public void revocationDuringDockerCallsPreventsWebsiteWritesAndCompensatesRemoteChanges() throws Exception {
        for (String stage : List.of("read", "write")) {
            try (var db = InMemoryZrLogDatabase.open()) {
                applicationConfig = db.config();
                plugin(db.dataSource());
                db.putWebsite("autoUpgradeVersion", 86400);
                var credentials = new AdminTokenVO();
                credentials.setUserId(1);
                var actor = db.component(AccountComponent.class).identities().account(credentials);
                var graph = db.component(AdminComponent.class);
                Runnable revoke = () -> {
                    try {
                        db.execute("update user set authVersion=authVersion+1 where userId=1");
                    } catch (SQLException error) {
                        throw new IllegalStateException(error);
                    }
                };
                AtomicBoolean revoked = new AtomicBoolean();
                Client client = new Client() {
                    @Override
                    public DockerUpdaterProtocol.SettingsResponse settings(String channel) {
                        var response = super.settings(channel);
                        if (stage.equals("read") && revoked.compareAndSet(false, true)) revoke.run();
                        return response;
                    }

                    @Override
                    public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
                        var saved = super.saveSettings(repository, channel);
                        if (stage.equals("write") && revoked.compareAndSet(false, true)) revoke.run();
                        return saved;
                    }
                };
                var service = service(client, true, graph.settings(), db.dataSource());
                AdminTokenThreadLocal.remove();
                var audit = new AdminAuditContext(1, "127.0.0.1", "/api/admin/website/upgrade", "Test worker");
                assertThrows(PermissionErrorException.class, () -> service.save(actor, input(ALIYUN), audit));
                assertTrue(revoked.get());
                assertEquals("86400", value(db, "autoUpgradeVersion"));
                assertEquals(HUB, client.repository);
                assertEquals("release", client.channel);
                assertEquals(stage.equals("write") ? List.of(ALIYUN + ":preview", HUB + ":release") : List.of(), client.saves);
                assertNull(AdminTokenThreadLocal.getUser());
            }
        }
    }

    @Test
    public void savesOneRequestWithoutPersistingRuntimeFieldsOrHiddenPackageSource() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(db.dataSource());
            db.putWebsite("upgradePackageSource", "github");
            Client client = new Client();
            UpgradeSettingsService service =
                    service(
                            client,
                            true,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())), db.dataSource());
            UpgradeSettingsRequest input = new Gson().fromJson("{\"autoUpgradeVersion\":-1,\"upgradePreview\":true,"
                    + "\"upgradePackageSource\":\"dl\",\"dockerRepository\":\"" + ALIYUN + "\",\"dockerMode\":false}", UpgradeSettingsRequest.class);
            service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input, AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId()));
            assertEquals(ALIYUN, client.repository);
            assertEquals("preview", client.channel);
            assertEquals("github", value(db, "upgradePackageSource"));
            assertEquals("-1", value(db, "autoUpgradeVersion"));
            assertEquals("true", value(db, "upgradePreview"));
            assertNull(value(db, "dockerRepository"));
            assertNull(value(db, "dockerMode"));
            assertNull(value(db, "dockerSettings"));
            var response = service.read(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null);
            assertTrue(response.isDockerMode());
            assertNull(response.getUpgradePackageSource());
            assertEquals(ALIYUN, response.getDockerSettings().settings.repository);
            assertTrue(new Gson().toJson(response).contains("\"dockerMode\":true"));
        }
    }

    @Test
    public void invalidRepositoryAndUnavailableUpdaterCannotPartiallySaveWebsiteSettings() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(db.dataSource());
            db.putWebsite("autoUpgradeVersion", 86400);
            Client client = new Client();
            var service =
                    service(
                            client,
                            true,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())), db.dataSource());
            assertThrows(ArgsException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input("unlisted.example/zrlog"), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertEquals("86400", value(db, "autoUpgradeVersion"));
            assertTrue(client.saves.isEmpty());
            client.failSave = true;
            assertThrows(DockerControl.RequestException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(ALIYUN), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertEquals("86400", value(db, "autoUpgradeVersion"));
            assertEquals(HUB, client.repository);
        }
    }

    @Test
    public void ordinaryChangesDoNotDependOnUpdaterAndKeepItsSavedRepository() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(db.dataSource());
            Client client = new Client();
            client.failRead = true;
            var service =
                    service(
                            client,
                            true,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())), db.dataSource());
            service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(null), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId()));
            assertEquals("-1", value(db, "autoUpgradeVersion"));
            assertEquals(0, client.reads);
            assertTrue(client.saves.isEmpty());
            var page = service.read(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null);
            assertTrue(page.isDockerMode());
            assertEquals("metadata_unavailable", page.getDockerErrorCode());
            assertNull(page.getDockerSettings());
        }
    }

    @Test
    public void restoresPreviousUpdaterSettingsWhenWebsiteWriteFails() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(db.dataSource());
            Client client = new Client();
            var service =
                    service(
                            client,
                            true,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())) {
                                @Override
                                public void updateUpgrade(
                                        UpgradeWebSiteInfo settings, AdminAuditContext audit)
                                        throws SQLException {
                                    throw new SQLException("test failure");
                                }
                            }, db.dataSource());
            assertThrows(SQLException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(ALIYUN), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertEquals(List.of(ALIYUN + ":preview", HUB + ":release"), client.saves);
            assertEquals(HUB, client.repository);
            assertEquals("release", client.channel);
        }
    }

    @Test
    public void packageModeNeverContactsUpdaterAndRejectsDockerWrites() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(db.dataSource());
            Client client = new Client();
            var service =
                    service(
                            client,
                            false,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())), db.dataSource());
            var input = input(null);
            input.setUpgradePackageSource("github");
            service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input, AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId()));
            assertEquals("github", service.read(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null).getUpgradePackageSource());
            assertFalse(service.read(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null).isDockerMode());
            assertThrows(ArgsException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(ALIYUN), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertEquals(0, client.reads);
            assertTrue(client.saves.isEmpty());
        }
    }

    @Test
    public void missingPluginAndInsufficientPermissionFailBeforeRemoteWrites() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            Client client = new Client();
            var service =
                    service(
                            client,
                            true,
                            new WebSiteSettingsService(
                                    new InMemoryZrLogDatabase.TestCacheService(),
                                    db.config() == null ? null : db.config().getTokenService(),
                                    () ->
                                            db.config() == null
                                                    ? null
                                                    : db.config()
                                                    .getPlugin(
                                                            UpdateVersionInfoPlugin.class),
                                    ApplicationServicesFixture.audit(db.dataSource()),
                                    ApplicationServicesFixture.websiteKv(db.dataSource()),
                                    () -> new WebSite(db.dataSource())), db.dataSource());
            assertThrows(IllegalStateException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(ALIYUN), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertEquals(0, client.reads);
            plugin(db.dataSource());
            db.execute("update user set role = 'editor' where userId = 1");
            assertThrows(PermissionErrorException.class, () -> service.save(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), input(ALIYUN), AdminAuditContexts.from(request(), AdminTokenThreadLocal.getUserId())));
            assertThrows(PermissionErrorException.class, () -> service.read(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), null));
            assertTrue(client.saves.isEmpty());
        }
    }

    private void plugin(DataSourceWrapper testDataSource) {
        applicationConfig
                .getAllPlugins()
                .add(
                        new UpdateVersionInfoPlugin(
                                ApplicationServicesFixture.upgradeNotices(applicationConfig),
                                () -> "zh_CN",
                                applicationConfig == null ? null : applicationConfig.getUpdater(), new WebsiteKvService(() -> new WebSite(testDataSource))) {
                            @Override
                            public boolean start() {
                                return true;
                            }

                            @Override
                            public boolean stop() {
                                return true;
                            }
                        });
    }

    private UpgradeSettingsService service(Client client, boolean docker, WebSiteSettingsService settings, DataSourceWrapper testDataSource) {
        return new UpgradeSettingsService(settings,
                () ->
                        UpdaterHosts.dockerControl(
                                applicationConfig == null ? null : applicationConfig.getUpdater()),
                AccountServicesFixture.component(testDataSource).permissions(),
                AccountServicesFixture.component(testDataSource).identities(),
                ApplicationServicesFixture.settings(testDataSource)) {
            @Override
            protected boolean isDockerMode() {
                return docker;
            }

            @Override
            protected DockerControl dockerClient() {
                return client;
            }
        };
    }

    private static class Client extends DockerControlStub {
        String repository = HUB;
        String channel = "release";
        int reads;
        boolean failRead, failSave;
        List<String> saves = new ArrayList<>();

        Client() {
        }

        @Override
        public DockerUpdaterProtocol.SettingsResponse settings(String requestedChannel) {
            reads++;
            if (failRead) throw new RequestException(502, "metadata_unavailable");
            var response = new DockerUpdaterProtocol.SettingsResponse();
            response.settings = new DockerUpdaterProtocol.Settings();
            response.settings.repository = repository;
            response.settings.channel = channel;
            response.currentRepository = HUB;
            var image = new DockerUpdaterProtocol.Image();
            image.repository = ALIYUN;
            response.images = List.of(image);
            return response;
        }

        @Override
        public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
            if (failSave) throw new RequestException(503);
            saves.add(repository + ":" + channel);
            this.repository = repository;
            this.channel = channel;
            var settings = new DockerUpdaterProtocol.Settings();
            settings.repository = repository;
            settings.channel = channel;
            return settings;
        }
    }
}
