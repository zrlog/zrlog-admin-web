package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.exception.AdminOriginException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.Log;
import com.zrlog.model.User;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;

/** Shared authorization boundary for admin controllers and future external adapters. */
@Singleton
public final class AccountPermissionService {
    private final jakarta.inject.Provider<User> users;
    private final jakarta.inject.Provider<Log> articles;

    @Inject
    public AccountPermissionService(
            jakarta.inject.Provider<User> users, jakarta.inject.Provider<Log> articles) {
        this.users = users;
        this.articles = articles;
    }

    public AccountAccess account(AdminTokenVO token) {
        try {
            if (token == null) throw new PermissionErrorException();
            AccountAccess account = AccountAccess.from(users.get().loadById(token.getUserId()));
            if (!account.isEnabled() || token.getAuthVersion() != account.getAuthVersion()) throw new PermissionErrorException();
            return DelegatedAccess.restrict(account);
        } catch (SQLException e) { throw new PermissionErrorException(); }
    }

    public AccountAccess current() {
        return account(AdminTokenThreadLocal.getUser());
    }

    public void administrator() {
        if (!current().isAdministrator()) throw new PermissionErrorException();
    }

    public Map<String, Object> article(AccountAccess account, long id, boolean publish)
            throws SQLException {
        Map<String, Object> row = articles.get().loadById(id);
        if (row == null || !(row.get("userId") instanceof Number)
                || !account.canAccessArticle(((Number) row.get("userId")).intValue(), AccountAccess.truth(row.get("privacy")))) throw new PermissionErrorException();
        if (publish && !account.canPublish()) throw new PermissionErrorException();
        return row;
    }

    public void readArticle(long id) throws SQLException {
        article(current(), id, false);
    }

    public static boolean isPublic(Map<String, Object> row) {
        return row != null && !AccountAccess.truth(row.get("privacy")) && !AccountAccess.truth(row.get("rubbish"));
    }

    public void require(AccountAction action) {
        if (!action.allowed(current())) throw new PermissionErrorException();
    }

    public static void requireAdminOrigin(HttpRequest request, BackendServerUrl backend) {
        try {
            backend.requireAdminRequestOrigin(request);
        } catch (IllegalArgumentException e) {
            throw new AdminOriginException();
        }
    }

    public void checkRoute(Method method, HttpRequest request) throws SQLException {
        if (method == null) throw new PermissionErrorException();
        RequiresAction binding = method.getAnnotation(RequiresAction.class);
        if (binding == null) throw new PermissionErrorException();
        AccountAccess account = current();
        if (!binding.value().allowed(account)) throw new PermissionErrorException();
        if (binding.articleQuery()) {
            String id = request.getParaToStr("id", "");
            if (id.isEmpty() && method.getName().equals("articleEdit")) return;
            try {
                long articleId = Long.parseLong(id);
                if (articleId <= 0) {
                    if (articleId != 0 && articleId != -account.getUserId()) throw new PermissionErrorException();
                } else article(account, articleId, false);
            } catch (NumberFormatException e) { throw new PermissionErrorException(); }
        }
        if (binding.articleIds()) {
            try {
                for (String id : request.getParaToStr("id", "").split(",")) article(account, Long.parseLong(id), false);
            } catch (NumberFormatException e) { throw new PermissionErrorException(); }
        }
    }
}
