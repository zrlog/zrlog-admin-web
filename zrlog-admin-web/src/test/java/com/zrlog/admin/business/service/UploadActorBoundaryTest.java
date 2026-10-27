package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.plugin.rest.response.UploadServiceResponseEntity;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.di.assets.AssetsComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class UploadActorBoundaryTest {
    private final boolean sqlite;
    private final String previousRoot = System.getProperty("sws.root.path");
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    public UploadActorBoundaryTest(boolean sqlite) {
        this.sqlite = sqlite;
    }

    @Parameterized.Parameters(name = "sqlite={0}")
    public static Boolean[] databases() {
        return new Boolean[]{false, true};
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(UploadActorBoundaryTest.class.getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContextPath")) return "/sub";
                    return null;
                });
    }

    @After
    public void restoreRoot() {
        if (previousRoot == null) System.clearProperty("sws.root.path");
        else System.setProperty("sws.root.path", previousRoot);
    }

    @Test
    public void explicitAuthorOwnsUploadedFilesTemporaryFilesAndAvatarWithoutThreadIdentity() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var uploads = db.component(AssetsComponent.class).uploads();
            var file = temporary.newFile("author.png");
            Files.writeString(file.toPath(), "author attachment");
            assertEquals(1, AdminTokenThreadLocal.getUserId());
            var result = uploads.saveUploadedFile(actor, file, "image", request(), null);
            assertTrue(result.getUrl().startsWith("/sub/attached/users/2/"));
            assertEquals("author attachment", Files.readString(PathUtil.getStaticFile(result.getUrl().substring(4)).toPath()));
            AdminTokenThreadLocal.remove();
            var transientFile = temporary.newFile("temporary.png");
            Files.writeString(transientFile.toPath(), "temporary author attachment");
            var stored = uploads.saveUploadedFile(actor, transientFile, AdminConstants.ADMIN_DB_ATTACHED_TMP, request(), null);
            assertTrue(stored.getUrl().startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/2/"));
            assertEquals("temporary author attachment", new String(Base64.getDecoder().decode(
                    (String) db.scalar("select value from website where name=?", "db_file" + stored.getUrl())), StandardCharsets.UTF_8));
            try (var input = uploads.openTemporaryResource(actor, stored.getUrl())) {
                assertEquals("temporary author attachment", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            db.execute("insert into user(userId,userName,role) values(3,'other-writer','author')");
            var otherActor = AccessActorBoundaryTest.actor(db, 3);
            assertThrows(PermissionErrorException.class, () -> uploads.openTemporaryResource(otherActor, stored.getUrl()));
            assertThrows(PermissionErrorException.class,
                    () -> uploads.openTemporaryResource(actor.restrictActions(Set.of("article.read")), stored.getUrl()));
            assertThrows(com.zrlog.common.exception.ArgsException.class,
                    () -> uploads.openTemporaryResource(actor, AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/2/../3/file.zip"));
            var avatarOnly = actor.restrictActions(Set.of("account.self"));
            var avatar = uploads.saveAvatarBytes(avatarOnly, "avatar".getBytes(StandardCharsets.UTF_8), "png", request(), null);
            assertTrue(avatar.getUrl().startsWith("/sub/attached/users/2/"));
            assertEquals("avatar", Files.readString(PathUtil.getStaticFile(avatar.getUrl().substring(4)).toPath()));
            assertThrows(PermissionErrorException.class, () -> uploads.saveThumbnailBytes(avatarOnly, new byte[]{1}, "png", request(), null));
            assertNull(AdminTokenThreadLocal.getUser());
        }
    }

    @Test
    public void mismatchedCredentialsAndRevokedActorsFailBeforeFilesystemChanges() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var uploads = db.component(AssetsComponent.class).uploads();
            var file = temporary.newFile("unchanged.png");
            Files.writeString(file.toPath(), "unchanged");
            var otherCredential = AdminTokenThreadLocal.getUser();
            assertThrows(PermissionErrorException.class, () -> uploads.saveUploadedFile(actor, file, "image", request(), otherCredential));
            var limited = actor.restrictActions(Set.of("article.read"));
            assertThrows(PermissionErrorException.class, () -> uploads.saveUploadedFile(limited, file, "image", request(), null));
            assertThrows(PermissionErrorException.class, () -> uploads.saveAvatarBytes(limited, new byte[]{1}, "png", request(), null));
            for (String revocation : new String[]{"enabled=false", "role='editor'", "authVersion=1"}) {
                db.execute("update user set enabled=true,role='author',authVersion=0 where userId=2");
                db.execute("update user set " + revocation + " where userId=2");
                assertThrows(PermissionErrorException.class, () -> uploads.saveUploadedFile(actor, file, "image", request(), null));
                assertThrows(PermissionErrorException.class, () -> uploads.saveThumbnailBytes(actor, new byte[]{1}, "png", request(), null));
                assertThrows(PermissionErrorException.class, () -> uploads.saveAvatarBytes(actor, new byte[]{1}, "png", request(), null));
                assertThrows(PermissionErrorException.class,
                        () -> uploads.openTemporaryResource(actor, AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/2/file.zip"));
            }
            assertEquals("unchanged", Files.readString(file.toPath()));
            assertFalse(PathUtil.getStaticFile("/attached").exists());
        }
    }

    @Test
    public void pluginTransportReceivesTheExplicitActorsCredential() throws Exception {
        try (var db = database()) {
            var actor = AccessActorBoundaryTest.actor(db, 2);
            var credential = new AdminTokenVO();
            credential.setUserId(2);
            credential.setSessionId("author-session");
            var forwarded = new AtomicInteger();
            db.config().getAllPlugins().add((PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                        if (method.getName().equals("requestService")) {
                            assertEquals(2, ((AdminTokenVO) args[2]).getUserId());
                            assertEquals("author-session", ((AdminTokenVO) args[2]).getSessionId());
                            assertEquals(1, AdminTokenThreadLocal.getUserId());
                            forwarded.incrementAndGet();
                            var response = new UploadServiceResponseEntity();
                            response.setUrl("https://cdn.example/author.png");
                            return new UploadServiceResponseEntity[]{response};
                        }
                        return null;
                    }));
            var file = temporary.newFile("cloud.png");
            Files.writeString(file.toPath(), "cloud attachment");
            var response = db.component(AssetsComponent.class).uploads()
                    .saveUploadedFile(actor, file, "image", request(), credential);
            assertEquals("https://cdn.example/author.png", response.getUrl());
            assertEquals(1, forwarded.get());
        }
    }

    private InMemoryZrLogDatabase database() throws Exception {
        System.setProperty("sws.root.path", temporary.newFolder("root").getAbsolutePath());
        var db = sqlite ? InMemoryZrLogDatabase.openSqlite() : InMemoryZrLogDatabase.open();
        try {
            db.execute("insert into user(userId,userName,role) values(2,'writer','author')");
            db.loadAdminWebModules();
            return db;
        } catch (Exception | Error error) {
            db.close();
            throw error;
        }
    }
}
