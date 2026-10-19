package com.zrlog.admin.web;

import static org.junit.Assert.assertEquals;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.api.FileManagerController;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.admin.web.interceptor.AdminInterceptor;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.di.DataComponent;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.stream.Collectors;

public class AssetsWebSetupInjectionTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void eachRequestUsesItsApplicationContextAndSeesUpdatedArticleReferences() throws Exception {
        String previousRoot = PathUtil.getRootPath();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            PathUtil.setRootPath(temporaryFolder.newFolder().getAbsolutePath());
            db.putWebsite(WebSiteService.FEATURE_RESOURCE_REFERENCE_ENABLED_KEY, true);
            db.execute("insert into log(logId,userId,typeId,title,content,version) values(?,?,?,?,?,?)",
                    7, 1, 1, "References", "<img src=\"/blog/attached/old.png\">", 0);
            ServerConfig server = new ServerConfig();
            server.setContextPath("/blog");
            server.getInterceptors().add(AdminInterceptor.class);
            server.getBindings()
                    .instance(
                            AdminPageDataFactory.class,
                            new AdminPageDataFactory(
                                    db.cacheService()::getPublicWebSiteInfo,
                                    () -> "",
                                    ApplicationServicesFixture.messages(),
                                    AccountServicesFixture.component().permissions()));
            var data = DaggerDataComponent.factory().create(db::dataSource, () -> true);
            server.getBindings()
                    .instance(DataComponent.class, data)
                    .instance(
                            ServiceComponent.class,
                            DaggerServiceComponent.factory()
                                    .create(
                                            data,
                                            () -> true,
                                            () -> null,
                                            () -> null,
                                            java.util.Collections::emptyList))
                    .instance(AccountComponent.class, AccountServicesFixture.component());
            new AssetsWebSetup(server, () -> null).setup();

            assertEquals(List.of("/attached/old.png"), missingReferences(server));
            db.execute("update log set content=?,version=version+1 where logId=7",
                    "<img src=\"/blog/attached/new.png\">");
            assertEquals(List.of("/attached/new.png"), missingReferences(server));
        } finally {
            PathUtil.setRootPath(previousRoot);
        }
    }

    private List<String> missingReferences(ServerConfig server) throws Exception {
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getServerConfig": return server;
                        case "getContextPath": return server.getContextPath();
                        case "getUri": return "/api/admin/file-manager";
                        case "getParaToStr": return "resourceType".equals(args[0]) ? "broken" : "";
                        default: return null;
                    }
                });
        var controller = (FileManagerController) Controller.buildController(
                FileManagerController.class.getMethod("index"), request, null);
        return controller.index().getData().getEntries().stream()
                .map(FileEntryVO::getPath)
                .collect(Collectors.toList());
    }
}
