package com.zrlog.admin.web.interceptor;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.GrantAuthority;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.business.service.WebhookService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.admin.web.McpWebSetup;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

public class BearerTokenInterceptorTest {

    private ZrLogConfig applicationConfig;

    public static class TestController extends Controller {
        private final AdminRequestIdentity permissions;

        TestController(AdminRequestIdentity permissions) {
            this.permissions = permissions;
        }

        @ResponseBody
        @RequiresAction(value = AccountAction.TAXONOMY_READ, descriptionKey = "taxonomy.list")
        public ApiStandardResponse<Boolean> read() {
            return new ApiStandardResponse<>(
                    DelegatedAccess.active()
                            && !permissions.current()
                                    .canPublish());
        }

        @ResponseBody @RequiresAction(value=AccountAction.SITE_CONFIGURE, descriptionKey="website.basic")
        public ApiStandardResponse<Boolean> write() { return new ApiStandardResponse<>(true); }
        @ResponseBody public ApiStandardResponse<Boolean> unbound() { return new ApiStandardResponse<>(true); }
        @ResponseBody @RequiresAction(value=AccountAction.TAXONOMY_READ, descriptionKey="taxonomy.list")
        public ApiStandardResponse<String> language() { return new ApiStandardResponse<>(I18nUtil.getCurrentLocale()); }

        @ResponseBody
        @RequiresAction(value = AccountAction.TAXONOMY_READ, descriptionKey = "taxonomy.list")
        public ApiStandardResponse<Void> failure() {
            throw new PermissionErrorException();
        }
    }
    @Test public void localizesResponsesAndControllerErrorsForTheTokenOwnerAndRestoresTheThread() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(); var language = AdminLanguageContext.open("zh_CN")) {
            applicationConfig = db.config();
            applicationConfig.getServerConfig().getBindings().controller(
                    TestController.class,
                    (resolver, request, response) -> new TestController(AccountServicesFixture.component(db.dataSource()).requestIdentity()));
            var config = applicationConfig.getServerConfig();
            config.getRouter().addMapper("/api/admin/test", TestController.class);
            db.putWebsite("language", "zh_CN");
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            PersonalTokenModels.Create body = new PersonalTokenModels.Create();
            body.name = "language"; body.permissionMode = "custom"; body.permissions = List.of("taxonomy.read");
            String token =
                    new PersonalAccessTokenService(
                                    new OAuthService(
                                                    ApplicationServicesFixture.backend(
                                                                    applicationConfig)
                                                            ::configured,
                                                    ApplicationServicesFixture.available(
                                                            applicationConfig, McpWebSetup.class),
                                                    AccountServicesFixture.component(db.dataSource())
                                                            .permissions(), AccountServicesFixture.component(db.dataSource())
                                                            .identities(),
                                                    AccountServicesFixture.component(db.dataSource()).security(),
                                                    tokenResource ->
                                                            new PersonalAccessTokenService(
                                                                    tokenResource,
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .identities(), AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .permissions(),
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .security()),
                                                    AccountServicesFixture.component(db.dataSource()).backend())
                                            .mcpResource(),
                                    AccountServicesFixture.component(db.dataSource()).identities(), AccountServicesFixture.component(db.dataSource()).permissions(),
                                    AccountServicesFixture.component(db.dataSource()).security())
                            .create(new GrantAuthority(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), !com.zrlog.admin.business.security.DelegatedAccess.restricted()), body)
                            .token;
            AdminTokenThreadLocal.remove();
            var parent = I18nUtil.threadLocal.get();
            assertEquals("en_US", ((ApiStandardResponse<?>) call("/api/admin/test/language", token, db.dataSource()).rendered).getData());
            var error = (ApiStandardResponse<?>) call("/api/admin/test/failure", token, db.dataSource()).rendered;
            assertEquals(9016, error.getError());
            try (var english = AdminLanguageContext.open("en_US")) {
                assertEquals(I18nUtil.getAdminBackendStringFromRes("admin.permission.error"), error.getMessage());
            }
            assertSame(parent, I18nUtil.threadLocal.get());
            assertEquals("zh_CN", parent.getLocale());
            assertNull(AdminTokenThreadLocal.getUser());
            db.execute("update user set preferences=null where userId=1");
            assertEquals("zh_CN", ((ApiStandardResponse<?>) call("/api/admin/test/language", token, db.dataSource()).rendered).getData());
            assertEquals(401, call("/api/admin/test/language", "invalid", db.dataSource()).status);
            assertSame(parent, I18nUtil.threadLocal.get());
        }
    }
    @Test public void bearerReusesActionBindingsWithoutCookieOrSessionFallback() throws Exception {
        try (InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            applicationConfig.getServerConfig().getBindings().controller(
                    TestController.class,
                    (resolver, request, response) -> new TestController(AccountServicesFixture.component(db.dataSource()).requestIdentity()));
            var config=db.config().getServerConfig(); config.getRouter().addMapper("/api/admin/test",TestController.class);
            PersonalTokenModels.Create body=new PersonalTokenModels.Create(); body.name="test"; body.permissionMode="custom"; body.permissions=List.of("taxonomy.read");
            var service =
                    new PersonalAccessTokenService(
                            new OAuthService(
                                            ApplicationServicesFixture.backend(db.config())
                                                    ::configured,
                                            ApplicationServicesFixture.available(
                                                    db.config(), McpWebSetup.class),
                                            AccountServicesFixture.component(db.dataSource()).permissions(), AccountServicesFixture.component(db.dataSource()).identities(),
                                            AccountServicesFixture.component(db.dataSource()).security(),
                                            tokenResource ->
                                                    new PersonalAccessTokenService(
                                                            tokenResource,
                                                            AccountServicesFixture.component(db.dataSource())
                                                                    .identities(), AccountServicesFixture.component(db.dataSource())
                                                                    .permissions(),
                                                            AccountServicesFixture.component(db.dataSource())
                                                                    .security()),
                                            AccountServicesFixture.component(db.dataSource()).backend())
                                    .mcpResource(),
                            AccountServicesFixture.component(db.dataSource()).identities(), AccountServicesFixture.component(db.dataSource()).permissions(),
                            AccountServicesFixture.component(db.dataSource()).security());
            String token=service.create(new GrantAuthority(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), !com.zrlog.admin.business.security.DelegatedAccess.restricted()), body).token;
            var previous=AdminTokenThreadLocal.getUser();
            Recorder success=call("/api/admin/test/read",token, db.dataSource());
            assertEquals(Boolean.TRUE,((ApiStandardResponse<?>)success.rendered).getData());
            assertFalse(success.headers.containsKey("Set-Cookie")); assertFalse(DelegatedAccess.active()); assertSame(previous,AdminTokenThreadLocal.getUser());
            assertEquals(403,call("/api/admin/test/write",token, db.dataSource()).status);
            assertEquals(403,call("/api/admin/test/unbound",token, db.dataSource()).status);
            assertEquals(403,call("/admin/user",token, db.dataSource()).status);
            assertEquals(401,call("/api/admin/test/read","bad-token", db.dataSource()).status);
            body.permissionMode=null; body.scopes=List.of("articles:read"); body.permissions=List.of();
            assertEquals(401,call("/api/admin/test/read",service.create(new GrantAuthority(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), !com.zrlog.admin.business.security.DelegatedAccess.restricted()), body).token, db.dataSource()).status);
            assertFalse(DelegatedAccess.active()); assertSame(previous,AdminTokenThreadLocal.getUser());
        }
    }
    private Recorder call(String uri,String token, DataSourceWrapper testDataSource) throws Exception {
        var config=applicationConfig.getServerConfig(); RequestConfig requestConfig=new RequestConfig(); requestConfig.setRouter(config.getRouter());
        HttpRequest request=(HttpRequest)Proxy.newProxyInstance(BearerTokenInterceptorTest.class.getClassLoader(),new Class[]{HttpRequest.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "getUri":return uri;
                case "getMethod":return HttpMethod.GET;
                case "getContextPath":return "";
                case "getHeader":return "Authorization".equals(args[0]) ? "Bearer "+token : "Host".equals(args[0]) ? "localhost:18080" : null;
                case "getHeaderMap":case "getParamMap":case "decodeParamMap":return Map.of();
                case "getRemoteHost":return "127.0.0.1";
                case "getServerConfig":return config;
                case "getRequestConfig":return requestConfig;
                case "toString":return "BearerRequest";
                default:return method.getReturnType().isPrimitive() ? 0 : null;
            }
        });
        Recorder recorder = new Recorder();
        BearerTokenInterceptor interceptor =
                new BearerTokenInterceptor(
                        () -> ApplicationServicesFixture.dispatcher(null, applicationConfig),
                        new OAuthService(
                                ApplicationServicesFixture.backend(applicationConfig)::configured,
                                ApplicationServicesFixture.available(
                                        applicationConfig, McpWebSetup.class),
                                AccountServicesFixture.component(testDataSource).permissions(), AccountServicesFixture.component(testDataSource).identities(),
                                AccountServicesFixture.component(testDataSource).security(),
                                tokenResource ->
                                        new PersonalAccessTokenService(
                                                tokenResource,
                                                AccountServicesFixture.component(testDataSource).identities(), AccountServicesFixture.component(testDataSource).permissions(),
                                                AccountServicesFixture.component(testDataSource).security()),
                                AccountServicesFixture.component(testDataSource).backend()),
                        AccountServicesFixture.component(testDataSource).routeAuthorizer(), AccountServicesFixture.component(testDataSource).requestIdentity(),
                        AccountServicesFixture.component(testDataSource).preferences(),
                        new WebhookService(
                                ApplicationServicesFixture.messages(testDataSource),
                                ApplicationServicesFixture.websiteCache(testDataSource)));
        assertTrue(interceptor.isHandleAble(request)); assertFalse(interceptor.doInterceptor(request,recorder.response())); return recorder;
    }
    private static class Recorder {
        int status=200; Object rendered; Map<String,String> headers=new HashMap<>();
        HttpResponse response() { return (HttpResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpResponse.class},(proxy,method,args)->{
            if(method.getName().equals("write") && args.length>1 && args[1] instanceof Integer) status=(Integer)args[1];
            if(method.getName().equals("renderJson")) rendered=args[0];
            if(method.getName().equals("addHeader")) headers.put(args[0].toString(),args[1].toString());
            return null;
        }); }
    }
}
