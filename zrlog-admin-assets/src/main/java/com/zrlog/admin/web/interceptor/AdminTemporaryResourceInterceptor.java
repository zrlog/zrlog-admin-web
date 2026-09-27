package com.zrlog.admin.web.interceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.business.util.FileEntryUtils;

import java.io.ByteArrayInputStream;
import java.util.Objects;

import static com.zrlog.util.CrossUtils.cross;

public class AdminTemporaryResourceInterceptor implements HandleAbleInterceptor {

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) throws Exception {
        cross(request, response);
        if (Objects.isNull(AdminInterceptorSupport.getAdminToken(request))) {
            response.renderCode(403);
            return false;
        }
        com.zrlog.data.security.AccountAccess actor = com.zrlog.admin.business.service.AccountPermissionService.account(AdminInterceptorSupport.getAdminToken(request));
        String uri = request.getUri();
        if (!actor.isAdministrator() && (!uri.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/" + actor.getUserId() + "/") || uri.contains("..") || uri.contains("%") || uri.contains("\\"))) {
            response.renderCode(403); return false;
        }
        response.addHeader("Cache-Control", "no-store");
        byte[] bytes = new DbFileService().loadDbFile(uri);
        response.addHeader("Content-Type", getMimeType(uri));
        response.write(new ByteArrayInputStream(bytes));
        return false;
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        return request.getUri().startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP);
    }

    private String getMimeType(String name) {
        String mimeType = FileEntryUtils.toMimeType(name);
        if (mimeType == null || mimeType.isEmpty()) {
            return "application/octet-stream";
        }
        return mimeType;
    }
}
