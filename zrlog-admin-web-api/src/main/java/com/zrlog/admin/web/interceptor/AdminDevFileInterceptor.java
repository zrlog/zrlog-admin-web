package com.zrlog.admin.web.interceptor;

import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.common.TokenService;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.function.Supplier;

public class AdminDevFileInterceptor implements HandleAbleInterceptor {
    private final AdminRequestIdentity requestIdentity;

    protected final Supplier<TokenService> tokenService;

    @Inject
    public AdminDevFileInterceptor(
            Supplier<TokenService> tokenService, AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;

        this.tokenService = Objects.requireNonNull(tokenService);
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(tokenService.get(), request))) {
            response.renderCode(403);
            return false;
        }
        if (!requestIdentity
                .account(AdminInterceptorSupport.getAdminToken(tokenService.get(), request))
                .isAdministrator()) {
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
