package com.zrlog.admin.web.interceptor;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 这个类负责了对所有的插件路由的代理中转给插件服务（及 plugin-core 这个进程）
 * 目前插件服务拦截了 /admin/plugins/* （进行权限检查）
 * 如果想了解更多关于插件的实现可以浏览这篇文章 <a href="https://blog.zrlog.com/zrlog-plugin-dev.html">插件实现</a>
 */
public class AdminPluginInterceptor implements HandleAbleInterceptor {

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
            AdminWebTools.blockUnLoginRequestHandler(request, response);
            return;
        }
        if (Constants.zrLogConfig.getPlugin(PluginCorePlugin.class).accessPlugin(target.replaceFirst(adminPluginUriPath, "/"), request, response, entry)) {
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
        new AdminAuditService().record(request, AdminAuditAction.PLUGIN_SURFACE_ACTION, pluginPath);
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
        if (Constants.zrLogConfig.getPlugin(PluginCorePlugin.class)
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
        AdminTokenVO entry = Constants.zrLogConfig.getTokenService().getAdminTokenVO(request);
        adminPermission(target, request, response, entry);
        return false;
    }
}
