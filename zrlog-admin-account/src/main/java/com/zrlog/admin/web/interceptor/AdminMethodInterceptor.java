package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared route matching and locked method dispatch, independent of the authentication method.
 */
public abstract class AdminMethodInterceptor implements HandleAbleInterceptor {
    private final Supplier<AdminActionDispatcher> dispatcher;

    protected AdminMethodInterceptor(Supplier<AdminActionDispatcher> dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    static boolean isAdminRequest(HttpRequest request) {
        if (Objects.equals(request.getUri(), AdminConstants.ADMIN_URI_BASE_PATH) || Objects.equals(request.getUri(), "/api" + AdminConstants.ADMIN_URI_BASE_PATH)) {
            return true;
        }
        return request.getUri().startsWith(AdminConstants.ADMIN_URI_BASE_PATH + "/") || request.getUri().startsWith("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/");
    }

    protected Method getMethod(HttpRequest request) {
        return request.getServerConfig().getRouter().getMethod(request.getUri(), request.getMethod());
    }

    protected void doMethodInterceptor(HttpRequest request, HttpResponse response, Method method) throws Exception {
        dispatcher.get().dispatch(request, response, method);
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return isAdminRequest(request);
    }
}
