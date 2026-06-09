package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;

import java.util.Objects;

public class AdminLoginInterceptor implements HandleAbleInterceptor {

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        try {
            String uri = request.getUri();
            if (AdminConstants.ADMIN_LOGIN_URI_PATH.equals(uri)) {
                if (Objects.isNull(AdminInterceptorSupport.getAdminToken(request))) {
                    new MethodInterceptor().doInterceptor(request, response);
                } else {
                    response.redirect(AdminConstants.ADMIN_URI_BASE_PATH + AdminConstants.INDEX_URI_PATH);
                }
            } else {
                new MethodInterceptor().doInterceptor(request, response);
            }
            return false;
        } finally {
            AdminTokenThreadLocal.remove();
        }
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        String uri = request.getUri();
        return AdminConstants.ADMIN_LOGIN_URI_PATH.equals(uri)
                || (AdminConstants.ADMIN_URI_BASE_PATH + "/logout").equals(uri)
                || ("/api" + AdminConstants.ADMIN_LOGIN_URI_PATH).equals(uri);
    }
}
