package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.plugin.BaseStaticSitePlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * 负责全部后台请求的处理（/admin/plugins/*,/api/admin/plugins/* 除外）
 */
public class AdminInterceptor implements HandleAbleInterceptor {

    private void validPluginToken(HttpRequest request) {
        String requestToken = request.getHeader("X-Plugin-Token");
        if (StringUtils.isEmpty(requestToken)) {
            throw new ArgsException("missing_token");
        }
        PluginCorePlugin plugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
        if (Objects.isNull(plugin) || !Objects.equals(plugin.getToken(), requestToken)) {
            throw new ArgsException("token");
        }
    }

    private void doRefreshCache(HttpRequest request, Method method) {
        RefreshCache annotation = method.getAnnotation(RefreshCache.class);
        if (Objects.nonNull(annotation)) {
            //跳过非更新
            if (annotation.onlyOnPostMethod() && request.getMethod() != HttpMethod.POST) {
                return;
            }
            //FaaS 强制同步完成请求
            boolean async = annotation.async() && !EnvKit.isFaaSMode();
            CacheUtils.updateCache(async, request, Arrays.asList(annotation.updateStaticSites()));
        }
    }

    private boolean isSseRefreshCacheRequest(HttpRequest request, Method method) {
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
        AdminStaticSiteSsePublisher.write(
                response,
                "refresh-cache-" + request.getUri(),
                siteTypes,
                emitter -> emitter.send("response", invoke),
                () -> {
                    CacheUtils.updateCache(false, request, siteTypes);
                },
                emitter -> emitter.send("refresh-complete", invoke)
        );
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
        return new DistributedLock("request-lock-" + request.getUri() + "-" + userFlag);
    }

    private AdminFullTokenVO getAdminToken(HttpRequest request) {
        if (!Constants.zrLogConfig.isInstalled()) {
            return null;
        }
        TokenService tokenService = Constants.zrLogConfig.getTokenService();
        if (Objects.isNull(tokenService)) {
            return null;
        }
        return tokenService.getAdminTokenVO(request);
    }

    /**
     * 为了规范代码，这里做了一点类是Spring的ResponseEntity的东西，及通过方法的返回值来判断是应该返回页面还会对应JSON数据
     * 具体方式看 AdminRouters，这里用到了 ThreadLocal
     */
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        try {
            String uri = request.getUri();
            if (AdminConstants.ADMIN_LOGIN_URI_PATH.equals(uri)) {
                if (Objects.isNull(getAdminToken(request))) {
                    new MethodInterceptor().doInterceptor(request, response);
                } else {
                    response.redirect(AdminConstants.ADMIN_URI_BASE_PATH + AdminConstants.INDEX_URI_PATH);
                }
                return false;
            }
            //拦截请求
            if (request.getUri().startsWith(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH)) {
                if (Objects.isNull(getAdminToken(request))) {
                    response.renderCode(403);
                    return false;
                }
            }
            if (Objects.equals(AdminConstants.ADMIN_REFRESH_CACHE_API_URI_PATH, uri)) {
                if (Objects.isNull(getAdminToken(request))) {
                    validPluginToken(request);
                }
                new MethodInterceptor().doInterceptor(request, response);
                return false;
            }
            if ((AdminConstants.ADMIN_URI_BASE_PATH + "/logout").equals(uri) || ("/api" + AdminConstants.ADMIN_LOGIN_URI_PATH).equals(uri)) {
                new MethodInterceptor().doInterceptor(request, response);
                return false;
            }
            Method method = request.getServerConfig().getRouter().getMethod(request.getUri(), request.getMethod());
            if (Objects.nonNull(method) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                AdminFullTokenVO adminTokenVO = getAdminToken(request);
                if (adminTokenVO == null) {
                    AdminWebTools.blockUnLoginRequestHandler(request, response);
                    return false;
                }
                Constants.zrLogConfig.getTokenService().setAdminToken(adminTokenVO.getUserId(), adminTokenVO.getSecretKey(),
                        adminTokenVO.getSessionId(), adminTokenVO.getProtocol(), request, response);
            }

            Lock lock = getLock(method, request);
            if (Objects.nonNull(lock)) {
                if (!lock.tryLock()) {
                    throw new ResourceLockedException();
                }
            }
            try {
                if (isSseRefreshCacheRequest(request, method)) {
                    doSseRefreshCache(request, response, method);
                } else {
                    new MethodInterceptor(() -> {
                        if (Objects.isNull(method)) {
                            return;
                        }
                        doRefreshCache(request, method);
                    }).doInterceptor(request, response);
                }
            } finally {
                if (Objects.nonNull(lock)) {
                    lock.unlock();
                }
            }
            return false;
        } finally {
            AdminTokenThreadLocal.remove();
        }
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        if (Objects.equals(request.getUri(), AdminConstants.ADMIN_URI_BASE_PATH) || Objects.equals(request.getUri(), "/api" + AdminConstants.ADMIN_URI_BASE_PATH)) {
            return true;
        }
        return request.getUri().startsWith(AdminConstants.ADMIN_URI_BASE_PATH + "/") || request.getUri().startsWith("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/");
    }
}
