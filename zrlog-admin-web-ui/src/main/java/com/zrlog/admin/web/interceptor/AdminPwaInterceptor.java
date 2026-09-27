package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.MimeTypeUtil;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.business.service.TemplateInfoHelper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 不检查 pwa 的 json 文件的请求
 */
public class AdminPwaInterceptor implements HandleAbleInterceptor {

    private final List<String> resourceUris = new ArrayList<>();

    public AdminPwaInterceptor() {
        resourceUris.add(AdminResource.ADMIN_ASSET_MANIFEST_JSON);
        TemplateInfoHelper.getClassPathTemplates().forEach(clazz -> {
            if (Objects.nonNull(clazz)) {
                resourceUris.add(clazz.getAdminPreviewImage());
            }
        });
        resourceUris.add(AdminConstants.ADMIN_SERVICE_WORKER_JS);
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        if (resourceUris.contains(request.getUri())) {
            return true;
        }
        return AdminConstants.ADMIN_PWA_MANIFEST_JSON.equals(request.getUri()) || AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH.equals(request.getUri());
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        if (Objects.equals(AdminConstants.ADMIN_SERVICE_WORKER_JS, request.getUri())) {
            response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(request.getUri().substring(request.getUri().lastIndexOf(".") + 1)));
            try (InputStream inputStream = AdminConstants.adminResource.renderServiceWorker(request)) {
                response.write(new ByteArrayInputStream(inputStream.readAllBytes()));
                return false;
            }
        }
        if (resourceUris.contains(request.getUri())) {
            response.addHeader("Content-Type", MimeTypeUtil.getMimeStrByExt(request.getUri().substring(request.getUri().lastIndexOf(".") + 1)));
            try (InputStream inputStream = AdminPwaInterceptor.class.getResourceAsStream(request.getUri())) {
                if (Objects.nonNull(inputStream)) {
                    response.write(new ByteArrayInputStream(inputStream.readAllBytes()));
                    return false;
                }
            }
        }
        if (AdminConstants.ADMIN_PWA_MANIFEST_JSON.equals(request.getUri()) || AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH.equals(request.getUri())) {
            Method method = request.getServerConfig().getRouter().getMethod(AdminConstants.ADMIN_PWA_MANIFEST_API_URI_PATH, request.getMethod());
            Object responseJson = method.invoke(Controller.buildController(method, request, response));
            if (responseJson != null) {
                response.renderJson(responseJson);
                return false;
            }
        }
        response.renderCode(404);
        return false;
    }
}
