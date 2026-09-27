package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class BearerTokenInterceptorTest {
    public static class TestController extends Controller {
        @ResponseBody @RequiresAction(value=AccountAction.TAXONOMY_READ, descriptionKey="taxonomy.list")
        public ApiStandardResponse<Boolean> read() { return new ApiStandardResponse<>(DelegatedAccess.active() && !AccountPermissionService.current().canPublish()); }
        @ResponseBody @RequiresAction(value=AccountAction.SITE_CONFIGURE, descriptionKey="website.basic")
        public ApiStandardResponse<Boolean> write() { return new ApiStandardResponse<>(true); }
        @ResponseBody public ApiStandardResponse<Boolean> unbound() { return new ApiStandardResponse<>(true); }
    }
    @Test public void bearerReusesActionBindingsWithoutCookieOrSessionFallback() throws Exception {
        try (InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            var config=Constants.zrLogConfig.getServerConfig(); config.getRouter().addMapper("/api/admin/test",TestController.class);
            PersonalTokenModels.Create body=new PersonalTokenModels.Create(); body.name="test"; body.permissionMode="custom"; body.permissions=List.of("taxonomy.read");
            var service=new PersonalAccessTokenService(new OAuthService().mcpResource());
            String token=service.create(body).token;
            var previous=AdminTokenThreadLocal.getUser();
            Recorder success=call("/api/admin/test/read",token);
            assertEquals(Boolean.TRUE,((ApiStandardResponse<?>)success.rendered).getData());
            assertFalse(success.headers.containsKey("Set-Cookie")); assertFalse(DelegatedAccess.active()); assertSame(previous,AdminTokenThreadLocal.getUser());
            assertEquals(403,call("/api/admin/test/write",token).status);
            assertEquals(403,call("/api/admin/test/unbound",token).status);
            assertEquals(403,call("/admin/user",token).status);
            assertEquals(401,call("/api/admin/test/read","bad-token").status);
            body.permissionMode=null; body.scopes=List.of("articles:read"); body.permissions=List.of();
            assertEquals(401,call("/api/admin/test/read",service.create(body).token).status);
            assertFalse(DelegatedAccess.active()); assertSame(previous,AdminTokenThreadLocal.getUser());
        }
    }
    private static Recorder call(String uri,String token) throws Exception {
        var config=Constants.zrLogConfig.getServerConfig(); RequestConfig requestConfig=new RequestConfig(); requestConfig.setRouter(config.getRouter());
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
        Recorder recorder=new Recorder(); BearerTokenInterceptor interceptor=new BearerTokenInterceptor();
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
