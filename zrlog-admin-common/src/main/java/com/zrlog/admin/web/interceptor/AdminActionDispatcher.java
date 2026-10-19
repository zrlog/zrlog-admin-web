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
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.util.BlogBuildInfoUtil;

import jakarta.inject.Inject;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/** Executes an authenticated action with its request lock and cache/publication policy. */
public final class AdminActionDispatcher {
    private final DistributedLock.Factory locks;

    private final CacheRefreshService cacheRefresh;
    private final AdminStaticSiteSsePublisher staticSitePublisher;

    @Inject
    public AdminActionDispatcher(
            CacheRefreshService cacheRefresh,
            AdminStaticSiteSsePublisher staticSitePublisher,
            DistributedLock.Factory locks) {
        this.locks = locks;

        this.cacheRefresh = cacheRefresh;
        this.staticSitePublisher = staticSitePublisher;
    }

    private Lock getLock(Method method, HttpRequest request) {
        if (Objects.isNull(method)) {
            return null;
        }
        RequestLock annotation = method.getAnnotation(RequestLock.class);
        if (Objects.isNull(annotation)) {
            return null;
        }
        if (annotation.onlyOnPostMethod() && request.getMethod() != HttpMethod.POST) {
            return null;
        }
        String userFlag = "";
        if (Objects.nonNull(AdminTokenThreadLocal.getUser())) {
            userFlag = AdminTokenThreadLocal.getUser().getSessionId();
        }
        return locks.create(
                "request-lock-"
                        + BlogBuildInfoUtil.getBuildId()
                        + request.getUri()
                        + "-"
                        + userFlag);
    }

    public void dispatch(HttpRequest request, HttpResponse response, Method method) throws Exception {
        Lock lock = getLock(method, request);
        if (Objects.nonNull(lock)) {
            if (!lock.tryLock()) {
                throw new ResourceLockedException();
            }
        }
        try {
            doRefreshAwareMethodInterceptor(request, response, method);
        } finally {
            if (Objects.nonNull(lock)) {
                lock.unlock();
            }
        }
    }

    private void doRefreshCache(HttpRequest request, Method method) {
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
        cacheRefresh.updateCache(async, request, Arrays.asList(annotation.updateStaticSites()));
    }

    private static boolean isSseRefreshCacheRequest(HttpRequest request, Method method) {
        if (Objects.isNull(method) || Objects.isNull(method.getAnnotation(RefreshCache.class))) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return StringUtils.isNotEmpty(accept) && accept.contains("text/event-stream");
    }

    private void doSseRefreshCache(HttpRequest request, HttpResponse response, Method method) throws Exception {
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
        staticSitePublisher.write(
                response,
                "refresh-cache-" + request.getUri(),
                siteTypes,
                emitter -> emitter.send("response", invoke),
                () -> cacheRefresh.updateCacheSynchronouslyOrThrow(request, siteTypes),
                emitter -> emitter.send("refresh-complete", invoke)
        );
    }

    private void doRefreshAwareMethodInterceptor(HttpRequest request, HttpResponse response, Method method) throws Exception {
        if (isSseRefreshCacheRequest(request, method)) {
            doSseRefreshCache(request, response, method);
            return;
        }
        new MethodInterceptor(() -> doRefreshCache(request, method)).doInterceptor(request, response);
    }
}
