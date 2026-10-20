package com.zrlog.admin.business.service;

import static org.junit.Assert.*;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.AssetsWebSetup;
import com.zrlog.admin.web.di.assets.AssetsComponent;
import com.zrlog.admin.web.di.assets.DaggerAssetsComponent;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.data.di.DaggerDataComponent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Set;

public class AttachmentStorageTest {
    @Rule public TemporaryFolder directory=new TemporaryFolder();
    @Test public void storesActualBytesUnderTheAccountDirectoryAndPreservesContextPath() throws Exception {
        String previous=System.getProperty("sws.root.path");
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            System.setProperty("sws.root.path",directory.newFolder().getAbsolutePath());
            db.execute("update user set role='author' where userId=1");
            HttpRequest request=(HttpRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpRequest.class},(proxy,method,args)->{
                if(method.getName().equals("getContextPath")) return "/sub";
                return null;
            });
            var data = DaggerDataComponent.factory().create(db::dataSource, () -> true);
            var services =
                    DaggerServiceComponent.factory()
                            .create(
                                    data,
                                    () -> true,
                                    () -> null,
                                    () -> null,
                                    java.util.Collections::emptyList);
            var account = AccountServicesFixture.component(db.dataSource());
            var content =
                    com.zrlog.admin.web.di.content.DaggerContentComponent.factory()
                            .create(
                                    data,
                                    services,
                                    account,
                                    () -> null,
                                    () -> null,
                                    com.zrlog.admin.support.ApplicationServicesFixture.publisher(
                                            data.cache(), db.config()),
                                    markdown -> null);
            db.config()
                    .getServerConfig()
                    .getBindings()
                    .instance(
                            AssetsComponent.class,
                            DaggerAssetsComponent.factory()
                                    .create(
                                            data,
                                            services,
                                            account,
                                            content::resources,
                                            () -> null,
                                            () -> db.config().getTokenService(),
                                            () -> false,
                                            ""));
            AssetsWebSetup storage=new AssetsWebSetup(db.config().getServerConfig(), () -> null);
            byte[] bytes="actual attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String url=storage.saveAttachment(account.requestIdentity().current(), bytes,"cover.PNG",request, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser());
            assertTrue(url,url.startsWith("/sub/attached/users/1/"));assertTrue(url.endsWith(".png"));
            assertFalse(url.contains("/sub/sub/"));
            assertArrayEquals(bytes,Files.readAllBytes(PathUtil.getStaticFile(url.substring("/sub".length())).toPath()));
            assertThrows(PermissionErrorException.class,()->DelegatedAccess.withPermissions(Set.of("article.read"),false,
                    ()->storage.saveAttachment(account.requestIdentity().current(), bytes,"cover.png",request, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser())));
            assertThrows(
                    ArgsException.class,
                    () -> storage.saveAttachment(account.requestIdentity().current(), bytes, "cover.png/escape", request, com.zrlog.admin.web.token.AdminTokenThreadLocal.getUser()));
        } finally {
            if(previous==null) System.clearProperty("sws.root.path");else System.setProperty("sws.root.path",previous);
        }
    }
}
