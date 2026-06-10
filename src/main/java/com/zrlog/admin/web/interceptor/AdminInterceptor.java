package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.BlogBuildInfoUtil;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * 负责全部后台请求的处理（/admin/plugins/*,/api/admin/plugins/* 除外）
 */
public class AdminInterceptor implements HandleAbleInterceptor {

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
        return new DistributedLock("request-lock-" + BlogBuildInfoUtil.getBuildId() + request.getUri() + "-" + userFlag);
    }

    protected Method getMethod(HttpRequest request) {
        return request.getServerConfig().getRouter().getMethod(request.getUri(), request.getMethod());
    }

    protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) throws Exception {
        Lock lock = getLock(method, request);
        if (Objects.nonNull(lock)) {
            if (!lock.tryLock()) {
                throw new ResourceLockedException();
            }
        }
        try {
            AdminInterceptorSupport.doRefreshAwareMethodInterceptor(request, response, method);
        } finally {
            if (Objects.nonNull(lock)) {
                lock.unlock();
            }
        }
    }

    /**
     * 为了规范代码，这里做了一点类是Spring的ResponseEntity的东西，及通过方法的返回值来判断是应该返回页面还会对应JSON数据
     * 具体方式看 AdminRouters，这里用到了 ThreadLocal
     */
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        try {
            Method method = getMethod(request);
            if (Objects.nonNull(method) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                AdminFullTokenVO adminTokenVO = AdminInterceptorSupport.getAdminToken(request);
                if (adminTokenVO == null) {
                    AdminWebTools.blockUnLoginRequestHandler(request, response);
                    return false;
                }
                Constants.zrLogConfig.getTokenService().setAdminToken(adminTokenVO.getUserId(), adminTokenVO.getSecretKey(),
                        adminTokenVO.getSessionId(), adminTokenVO.getProtocol(), request, response);
            }

            doMethodInterceptor(request, response, method);
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
