package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.BaseStaticSitePlugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 负责全部后台请求的处理（/admin/plugins/*,/api/admin/plugins/* 除外）
 */
public class AdminInterceptor extends AdminMethodInterceptor {
    protected final Supplier<TokenService> tokenService;
    private final BooleanSupplier installed;

    public AdminInterceptor(Supplier<TokenService> tokenService, Supplier<AdminActionDispatcher> dispatcher, BooleanSupplier installed) {
        super(dispatcher);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.installed = Objects.requireNonNull(installed);
    }

    /**
     * 为了规范代码，这里做了一点类是Spring的ResponseEntity的东西，及通过方法的返回值来判断是应该返回页面还会对应JSON数据
     * 具体方式看 AdminRouters，这里用到了 ThreadLocal
     */
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        try {
            Method method = getMethod(request);
            if (Objects.nonNull(method) && !BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                AdminFullTokenVO adminTokenVO = AdminInterceptorSupport.getAdminToken(tokenService.get(), request);
                if (adminTokenVO == null) {
                    AdminWebTools.blockUnLoginRequestHandler(request, response, installed.getAsBoolean());
                    return false;
                }
                tokenService.get().refreshAdminToken(adminTokenVO, request, response);
            }

            if (!BaseStaticSitePlugin.isStaticPluginRequest(request)) {
                response.addHeader("Cache-Control", "no-store");
                response.addHeader("Referrer-Policy", "no-referrer");
                response.addHeader("X-Frame-Options", "DENY");
                if (java.util.Set.of("/admin", "/admin/", "/admin/index", "/admin/index.html").contains(request.getUri())
                        && !com.zrlog.admin.business.service.AccountPermissionService.current().isAdministrator()) {
                    response.redirect(request.getContextPath() + "/admin/article"); return false;
                }
                com.zrlog.admin.business.service.AccountPermissionService.checkRoute(method, request);
                com.zrlog.common.vo.I18nVO locale = com.zrlog.util.I18nUtil.threadLocal.get();
                if (locale != null) {
                    locale.setLocale(new com.zrlog.admin.business.service.UserPreferenceService().effective().language);
                }
            }
            doMethodInterceptor(request, response, method);
            return false;
        } finally {
            AdminTokenThreadLocal.remove();
        }
    }

}
