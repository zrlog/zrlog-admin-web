package com.zrlog.admin.web.interceptor;

import com.hibegin.common.util.IOUtil;
import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.MethodInterceptor;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.common.Constants;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.StaticFileCacheUtils;
import com.zrlog.util.ZrLogUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Objects;

public class AdminStaticResourceInterceptor implements HandleAbleInterceptor {


    public AdminStaticResourceInterceptor() {
    }

    private boolean isAdminMainJs(String uri) {
        return uri.contains(Constants.ADMIN_URI_BASE_PATH + "/static/js/main.") && uri.endsWith(".js");
    }

    /**
     * 让子目录能正确加载依赖的 js 文件
     *
     * @param uri
     * @return
     */
    private String geAdminMainJsContent(String uri) {
        InputStream inputStream = StaticFileCacheUtils.class.getResourceAsStream(uri);
        if (Objects.isNull(inputStream)) {
            return "";
        }
        String stringInputStream = IOUtil.getStringInputStream(inputStream);
        String adminPath = "admin/";
        return stringInputStream.replace("\"" + AdminConstants.ADMIN_URI_BASE_PATH + "/\"", "document.currentScript.baseURI + \"" + adminPath + "\"");
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        ZrLogUtil.putLongTimeCache(response);
        //打包过后的静态资源文件进行拦截
        if (isAdminMainJs(request.getUri())) {
            response.getHeader().put("Content-Type", "application/javascript;charset=" + request.getRequestConfig().getCharSet());
            response.write(new ByteArrayInputStream(geAdminMainJsContent(request.getUri()).getBytes()), 200);
            return false;
        }
        AdminStaticResourcePlugin staticSitePlugin = Constants.zrLogConfig.getPlugin(AdminStaticResourcePlugin.class);
        if (Objects.nonNull(staticSitePlugin)) {
            File cacheFile = staticSitePlugin.loadCacheFile(request);
            if (cacheFile.isFile() && cacheFile.exists()) {
                response.writeFile(cacheFile);
                return false;
            }
        }
        new MethodInterceptor().doInterceptor(request, response);
        return false;
    }

    /**
     * staticPlugin，自己控制缓存文件的读取和生成方式
     *
     * @param request
     * @return
     */
    @Override
    public boolean isHandleAble(HttpRequest request) {
        if (Objects.equals(request.getUri(), AdminConstants.ADMIN_SERVICE_WORKER_JS)) {
            return true;
        }
        if (BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            return false;
        }
        return AdminConstants.adminStaticResources.stream().anyMatch(e -> request.getUri().startsWith(e + "/"));
    }
}
