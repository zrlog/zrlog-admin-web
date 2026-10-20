package com.zrlog.admin.web.security;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.AdminOriginException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAccess;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.sql.SQLException;

/** HTTP route metadata and parameter parsing stay at the Web boundary. */
@Singleton
public final class AdminRouteAuthorizer {
    private final AccountPermissionService permissions;
    private final BackendServerUrl backend;

    @Inject
    public AdminRouteAuthorizer(AccountPermissionService permissions, BackendServerUrl backend) {
        this.permissions = permissions;
        this.backend = backend;
    }

    public void requireAdminOrigin(HttpRequest request) {
        try {
            backend.requireAdminRequestOrigin(request);
        } catch (IllegalArgumentException e) {
            throw new AdminOriginException();
        }
    }

    public void checkRoute(AccountAccess account, Method method, HttpRequest request) throws SQLException {
        if (method == null) throw new PermissionErrorException();
        RequiresAction binding = method.getAnnotation(RequiresAction.class);
        if (binding == null) throw new PermissionErrorException();
        permissions.require(account, binding.value());
        if (binding.articleQuery()) {
            String id = request.getParaToStr("id", "");
            if (id.isEmpty() && binding.optionalArticleId()) return;
            try {
                long articleId = Long.parseLong(id);
                if (articleId <= 0) {
                    if (articleId != 0 && articleId != -account.getUserId()) throw new PermissionErrorException();
                } else permissions.readArticle(account, articleId);
            } catch (NumberFormatException e) {
                throw new PermissionErrorException();
            }
        }
        if (binding.articleIds()) {
            try {
                for (String id : request.getParaToStr("id", "").split(",")) {
                    permissions.readArticle(account, Long.parseLong(id));
                }
            } catch (NumberFormatException e) {
                throw new PermissionErrorException();
            }
        }
    }
}
