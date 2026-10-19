package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.UpgradeSettingsRequest;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.spi.DockerControl;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class UpgradeSettingsServiceTest {

    private com.zrlog.common.ZrLogConfig applicationConfig;
    private static final String HUB = "docker.io/zrlog/zrlog";
    private static final String ALIYUN = "registry.cn-chengdu.aliyuncs.com/hibegin/zrlog";

    @Test public void savesOneRequestWithoutPersistingRuntimeFieldsOrHiddenPackageSource() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin();
            db.putWebsite("upgradePackageSource", "github");
            Client client = new Client();
            UpgradeSettingsService service = service(client, true, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)));
            UpgradeSettingsRequest input = new Gson().fromJson("{\"autoUpgradeVersion\":-1,\"upgradePreview\":true,"
                    + "\"upgradePackageSource\":\"dl\",\"dockerRepository\":\"" + ALIYUN + "\",\"dockerMode\":false}", UpgradeSettingsRequest.class);
            service.save(input, request());
            assertEquals(ALIYUN, client.repository);
            assertEquals("preview", client.channel);
            assertEquals("github", value(db, "upgradePackageSource"));
            assertEquals("-1", value(db, "autoUpgradeVersion"));
            assertEquals("true", value(db, "upgradePreview"));
            assertNull(value(db, "dockerRepository"));
            assertNull(value(db, "dockerMode"));
            assertNull(value(db, "dockerSettings"));
            var response = service.read(null);
            assertTrue(response.isDockerMode());
            assertNull(response.getUpgradePackageSource());
            assertEquals(ALIYUN, response.getDockerSettings().settings.repository);
            assertTrue(new Gson().toJson(response).contains("\"dockerMode\":true"));
        }
    }

    @Test public void invalidRepositoryAndUnavailableUpdaterCannotPartiallySaveWebsiteSettings() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(); db.putWebsite("autoUpgradeVersion", 86400);
            Client client = new Client();
            var service = service(client, true, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)));
            assertThrows(ArgsException.class, () -> service.save(input("unlisted.example/zrlog"), request()));
            assertEquals("86400", value(db, "autoUpgradeVersion"));
            assertTrue(client.saves.isEmpty());
            client.failSave = true;
            assertThrows(DockerControl.RequestException.class, () -> service.save(input(ALIYUN), request()));
            assertEquals("86400", value(db, "autoUpgradeVersion"));
            assertEquals(HUB, client.repository);
        }
    }

    @Test public void ordinaryChangesDoNotDependOnUpdaterAndKeepItsSavedRepository() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin();
            Client client = new Client(); client.failRead = true;
            var service = service(client, true, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)));
            service.save(input(null), request());
            assertEquals("-1", value(db, "autoUpgradeVersion"));
            assertEquals(0, client.reads);
            assertTrue(client.saves.isEmpty());
            var page = service.read(null);
            assertTrue(page.isDockerMode());
            assertEquals("metadata_unavailable", page.getDockerErrorCode());
            assertNull(page.getDockerSettings());
        }
    }

    @Test public void restoresPreviousUpdaterSettingsWhenWebsiteWriteFails() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(); Client client = new Client();
            var service = service(client, true, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)) {
                @Override public void updateUpgrade(UpgradeWebSiteInfo settings, HttpRequest request) throws SQLException {
                    throw new SQLException("test failure");
                }
            });
            assertThrows(SQLException.class, () -> service.save(input(ALIYUN), request()));
            assertEquals(List.of(ALIYUN + ":preview", HUB + ":release"), client.saves);
            assertEquals(HUB, client.repository);
            assertEquals("release", client.channel);
        }
    }

    @Test public void packageModeNeverContactsUpdaterAndRejectsDockerWrites() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            plugin(); Client client = new Client();
            var service = service(client, false, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)));
            var input = input(null); input.setUpgradePackageSource("github");
            service.save(input, request());
            assertEquals("github", service.read(null).getUpgradePackageSource());
            assertFalse(service.read(null).isDockerMode());
            assertThrows(ArgsException.class, () -> service.save(input(ALIYUN), request()));
            assertEquals(0, client.reads);
            assertTrue(client.saves.isEmpty());
        }
    }

    @Test public void missingPluginAndInsufficientPermissionFailBeforeRemoteWrites() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            Client client = new Client(); var service = service(client, true, new WebSiteSettingsService(new InMemoryZrLogDatabase.TestCacheService(), db.config() == null ? null : db.config().getTokenService(), () -> db.config() == null ? null : db.config().getPlugin(com.zrlog.business.updater.UpdateVersionInfoPlugin.class)));
            assertThrows(IllegalStateException.class, () -> service.save(input(ALIYUN), request()));
            assertEquals(0, client.reads);
            plugin(); db.execute("update user set role = 'editor' where userId = 1");
            assertThrows(PermissionErrorException.class, () -> service.save(input(ALIYUN), request()));
            assertThrows(PermissionErrorException.class, () -> service.read(null));
            assertTrue(client.saves.isEmpty());
        }
    }

    private void plugin() {
        applicationConfig.getAllPlugins().add(new UpdateVersionInfoPlugin(com.zrlog.admin.support.ApplicationServicesFixture.upgradeNotices(applicationConfig), () -> "zh_CN", applicationConfig == null ? null : applicationConfig.getUpdater()) {
            @Override public boolean start() { return true; }
            @Override public boolean stop() { return true; }
        });
    }
    private UpgradeSettingsService service(Client client, boolean docker, WebSiteSettingsService settings) {
        return new UpgradeSettingsService(settings, () -> com.zrlog.common.adapter.UpdaterHosts.dockerControl(applicationConfig == null ? null : applicationConfig.getUpdater())) {
            @Override protected boolean isDockerMode() { return docker; }
            @Override protected DockerControl dockerClient() { return client; }
        };
    }
    private static UpgradeSettingsRequest input(String repository) {
        UpgradeSettingsRequest input = new UpgradeSettingsRequest();
        input.setAutoUpgradeVersion(-1); input.setUpgradePreview(true); input.setDockerRepository(repository);
        return input;
    }
    private static Object value(InMemoryZrLogDatabase db, String key) throws SQLException {
        return db.scalar("select value from website where name = ?", key);
    }
    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> method.getName().equals("getUri") ? "/api/admin/website/upgrade" : null);
    }
    private static class Client extends com.zrlog.admin.test.support.DockerControlStub {
        String repository = HUB;
        String channel = "release";
        int reads;
        boolean failRead, failSave;
        List<String> saves = new ArrayList<>();
        Client() { }
        @Override public DockerUpdaterProtocol.SettingsResponse settings(String requestedChannel) {
            reads++;
            if (failRead) throw new RequestException(502, "metadata_unavailable");
            var response = new DockerUpdaterProtocol.SettingsResponse();
            response.settings = new DockerUpdaterProtocol.Settings();
            response.settings.repository = repository; response.settings.channel = channel;
            response.currentRepository = HUB;
            var image = new DockerUpdaterProtocol.Image(); image.repository = ALIYUN;
            response.images = List.of(image);
            return response;
        }
        @Override public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
            if (failSave) throw new RequestException(503);
            saves.add(repository + ":" + channel); this.repository = repository; this.channel = channel;
            var settings = new DockerUpdaterProtocol.Settings(); settings.repository = repository; settings.channel = channel;
            return settings;
        }
    }
}
