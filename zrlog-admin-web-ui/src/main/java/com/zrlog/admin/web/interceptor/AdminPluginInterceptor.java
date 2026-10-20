package com.zrlog.admin.web.interceptor;

import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminTokenVO;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 这个类负责了对所有的插件路由的代理中转给插件服务（及 plugin-core 这个进程）
 * 目前插件服务拦截了 /admin/plugins/* （进行权限检查）
 * 如果想了解更多关于插件的实现可以浏览这篇文章 <a href="https://blog.zrlog.com/zrlog-plugin-dev.html">插件实现</a>
 */
public class AdminPluginInterceptor implements HandleAbleInterceptor {
    private final AdminAuditService adminAuditService;

    private final AdminRequestIdentity requestIdentity;

    protected final Supplier<TokenService> tokenService;

    private final Supplier<PluginCorePlugin> pluginCore;
    private final BooleanSupplier installed;

    @Inject
    public AdminPluginInterceptor(
            Supplier<TokenService> tokenService,
            Supplier<PluginCorePlugin> pluginCore,
            @Named("installed") BooleanSupplier installed,
            AdminRequestIdentity requestIdentity,
            AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;

        this.requestIdentity = requestIdentity;

        this.tokenService = Objects.requireNonNull(tokenService);
        this.pluginCore = Objects.requireNonNull(pluginCore);
        this.installed = Objects.requireNonNull(installed);
    }

    private static final String notGoodAdminUriPath = Constants.ADMIN_URI_BASE_PATH + "/plugins";
    private static final String adminPluginUriPath = Constants.ADMIN_URI_BASE_PATH + "/plugins/";
    private static final List<String> PUBLIC_PLUGIN_PWA_RESOURCES = Arrays.asList(
            "manifest.webmanifest",
            "manifest.json",
            "pwa-icon",
            "pwa-sw.js"
    );
    private final List<String> pluginHandlerPaths = Arrays.asList(notGoodAdminUriPath, adminPluginUriPath);

    @Override
    public boolean isHandleAble(HttpRequest request) {
        String actionKey = request.getUri();
        return pluginHandlerPaths.stream().anyMatch(actionKey::startsWith);
    }

    /**
     * 检查是否登录，未登录的请求直接放回403的错误页面
     *
     * @param target
     * @param request
     * @param response
     * @param entry
     * @throws IOException
     */
    private void adminPermission(String target, HttpRequest request, HttpResponse response, AdminTokenVO entry) throws IOException, URISyntaxException, InterruptedException {
        if (Objects.isNull(entry)) {
            AdminWebTools.blockUnLoginRequestHandler(request, response, installed.getAsBoolean());
            return;
        }
        if (!requestIdentity.account(entry).isAdministrator()) {
            response.renderCode(403);
            return;
        }
        if (pluginCore.get().accessPlugin(target.replaceFirst(adminPluginUriPath, "/"), request, response, entry)) {
            recordPluginSurfaceAction(request, target);
            return;
        }
        response.renderCode(404);
    }

    private void recordPluginSurfaceAction(HttpRequest request, String target) {
        if (request.getMethod() != HttpMethod.POST || !target.startsWith(adminPluginUriPath)) {
            return;
        }
        String pluginPath = target.substring(adminPluginUriPath.length())
                .split("\\?", 2)[0]
                .replaceAll("^/+|/+$", "");
        if (!pluginPath.endsWith("/surfaceAction")) {
            return;
        }
        this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.PLUGIN_SURFACE_ACTION, pluginPath);
    }

    static boolean isPluginPwaResource(String target) {
        if (Objects.isNull(target) || !target.startsWith(adminPluginUriPath)) {
            return false;
        }
        String path = target.split("\\?", 2)[0];
        String relativePath = path.substring(adminPluginUriPath.length());
        String[] segments = relativePath.split("/", -1);
        return segments.length == 2
                && !segments[0].isEmpty()
                && PUBLIC_PLUGIN_PWA_RESOURCES.contains(segments[1]);
    }

    static String pluginCoreUri(String target) {
        return target.replaceFirst(adminPluginUriPath, "/");
    }

    private void renderPublicPluginPwaResource(String target, HttpRequest request, HttpResponse response)
            throws IOException, URISyntaxException, InterruptedException {
        if (pluginCore.get()
                .accessPlugin(pluginCoreUri(target), request, response, null)) {
            return;
        }
        response.renderCode(404);
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        String target = request.getUri();
        if (Objects.equals(notGoodAdminUriPath, target)) {
            response.redirect(adminPluginUriPath);
            return false;
        }
        if (isPluginPwaResource(target)) {
            renderPublicPluginPwaResource(target, request, response);
            return false;
        }
        AdminTokenVO entry = AdminInterceptorSupport.getAdminToken(tokenService.get(), request);
        adminPermission(target, request, response, entry);
        return false;
    }
}
