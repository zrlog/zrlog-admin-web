package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;

import java.lang.reflect.Method;
import java.util.Objects;

public class AdminRefreshCacheInterceptor extends AdminInterceptor {

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(request))) {
            PluginTokenValidator.validate(request);
        } else if (!com.zrlog.admin.business.service.AccountPermissionService.account(AdminInterceptorSupport.getAdminToken(request)).isAdministrator()) {
            response.renderCode(403); return false;
        }
        Method method = getMethod(request);
        doMethodInterceptor(request, response, method);
        return false;
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return Objects.equals(AdminConstants.ADMIN_REFRESH_CACHE_API_URI_PATH, request.getUri());
    }
}
