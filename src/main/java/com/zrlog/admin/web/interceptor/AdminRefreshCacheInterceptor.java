package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.ArgsException;

import java.lang.reflect.Method;
import java.util.Objects;

public class AdminRefreshCacheInterceptor extends AdminInterceptor {

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(request))) {
            validPluginToken(request);
        }
        Method method = getMethod(request);
        doMethodInterceptor(request, response, method);
        return false;
    }

    private void validPluginToken(HttpRequest request) {
        String requestToken = request.getHeader("X-Plugin-Token");
        if (StringUtils.isEmpty(requestToken)) {
            throw new ArgsException("missing_token");
        }
        PluginCorePlugin plugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
        if (Objects.isNull(plugin) || !Objects.equals(plugin.getToken(), requestToken)) {
            throw new ArgsException("token");
        }
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return Objects.equals(AdminConstants.ADMIN_REFRESH_CACHE_API_URI_PATH, request.getUri());
    }
}
