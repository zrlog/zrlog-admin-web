package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.common.TokenService;

import jakarta.inject.Inject;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

public class AdminRefreshCacheInterceptor extends AdminMethodInterceptor {
    private final AccountPermissionService permissionService;

    private final PluginTokenValidator pluginTokens;
    private final Supplier<TokenService> tokenService;

    @Inject
    public AdminRefreshCacheInterceptor(
            Supplier<TokenService> tokenService,
            PluginTokenValidator pluginTokens,
            Supplier<AdminActionDispatcher> dispatcher,
            AccountPermissionService permissionService) {
        super(dispatcher);
        this.permissionService = permissionService;

        this.tokenService = Objects.requireNonNull(tokenService);
        this.pluginTokens = pluginTokens;
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(tokenService.get(), request))) {
            pluginTokens.validate(request);
        } else if (!permissionService
                .account(AdminInterceptorSupport.getAdminToken(tokenService.get(), request))
                .isAdministrator()) {
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
