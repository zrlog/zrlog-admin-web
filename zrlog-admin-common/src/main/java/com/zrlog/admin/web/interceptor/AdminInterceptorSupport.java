package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class AdminInterceptorSupport {

    private AdminInterceptorSupport() {
    }

    static AdminFullTokenVO getAdminToken(HttpRequest request) {
        if (!Constants.zrLogConfig.isInstalled()) {
            return null;
        }
        TokenService tokenService = Constants.zrLogConfig.getTokenService();
        if (Objects.isNull(tokenService)) {
            return null;
        }
        return tokenService.getAdminTokenVO(request);
    }

    private static void doRefreshCache(HttpRequest request, Method method) {
        if (Objects.isNull(method)) {
            return;
        }
        RefreshCache annotation = method.getAnnotation(RefreshCache.class);
        if (Objects.isNull(annotation)) {
            return;
        }
        if (annotation.onlyOnPostMethod() && request.getMethod() != HttpMethod.POST) {
            return;
        }
        boolean async = annotation.async() && !EnvKit.isFaaSMode();
        CacheUtils.updateCache(async, request, Arrays.asList(annotation.updateStaticSites()));
    }

    private static boolean isSseRefreshCacheRequest(HttpRequest request, Method method) {
        if (Objects.isNull(method) || Objects.isNull(method.getAnnotation(RefreshCache.class))) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return StringUtils.isNotEmpty(accept) && accept.contains("text/event-stream");
    }

    private static void doSseRefreshCache(HttpRequest request, HttpResponse response, Method method) throws Exception {
        RefreshCache annotation = method.getAnnotation(RefreshCache.class);
        if (annotation.onlyOnPostMethod() && request.getMethod() != HttpMethod.POST) {
            new MethodInterceptor().doInterceptor(request, response);
            return;
        }
        Object invoke = method.invoke(Controller.buildController(method, request, response));
        if (Objects.isNull(method.getAnnotation(ResponseBody.class))) {
            return;
        }
        List<StaticSiteType> siteTypes = Arrays.asList(annotation.updateStaticSites());
        AdminStaticSiteSsePublisher.write(
                response,
                "refresh-cache-" + request.getUri(),
                siteTypes,
                emitter -> emitter.send("response", invoke),
                () -> CacheUtils.updateCacheSynchronouslyOrThrow(request, siteTypes),
                emitter -> emitter.send("refresh-complete", invoke)
        );
    }

    static void doRefreshAwareMethodInterceptor(HttpRequest request, HttpResponse response, Method method) throws Exception {
        if (isSseRefreshCacheRequest(request, method)) {
            doSseRefreshCache(request, response, method);
            return;
        }
        new MethodInterceptor(() -> doRefreshCache(request, method)).doInterceptor(request, response);
    }
}
