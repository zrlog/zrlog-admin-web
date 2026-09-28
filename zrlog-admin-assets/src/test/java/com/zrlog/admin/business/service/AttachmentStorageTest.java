package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.AssetsWebSetup;
import com.zrlog.common.Constants;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Set;
import static org.junit.Assert.*;

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
            AssetsWebSetup storage=new AssetsWebSetup(Constants.zrLogConfig.getServerConfig());
            byte[] bytes="actual attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String url=storage.saveAttachment(bytes,"cover.PNG",request);
            assertTrue(url,url.startsWith("/sub/attached/users/1/"));assertTrue(url.endsWith(".png"));
            assertFalse(url.contains("/sub/sub/"));
            assertArrayEquals(bytes,Files.readAllBytes(PathUtil.getStaticFile(url.substring("/sub".length())).toPath()));
            assertThrows(PermissionErrorException.class,()->DelegatedAccess.withPermissions(Set.of("article.read"),false,
                    ()->storage.saveAttachment(bytes,"cover.png",request)));
            assertThrows(com.zrlog.common.exception.ArgsException.class,()->storage.saveAttachment(bytes,"cover.png/escape",request));
        } finally {
            if(previous==null) System.clearProperty("sws.root.path");else System.setProperty("sws.root.path",previous);
        }
    }
}
