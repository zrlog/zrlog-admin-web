package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;

import java.util.Objects;

public class AdminDevFileInterceptor implements HandleAbleInterceptor {

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(request))) {
            response.renderCode(403);
            return false;
        }
        return true;
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return request.getUri().startsWith(AdminConstants.ADMIN_DEV_FILE_URI_BASE_PATH);
    }
}
