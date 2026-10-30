package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.model.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.Map;

/**
 * Business authorization for an explicitly supplied, authenticated account.
 */
@Singleton
public final class AccountPermissionService {
    private final jakarta.inject.Provider<Log> articles;

    @Inject
    public AccountPermissionService(jakarta.inject.Provider<Log> articles) {
        this.articles = articles;
    }

    public static boolean isPublic(Map<String, Object> row) {
        return row != null && !AccountAccess.truth(row.get("privacy")) && !AccountAccess.truth(row.get("rubbish"));
    }

    public void administrator(AccountAccess account) {
        if (account == null || !account.isAdministrator()) throw new PermissionErrorException();
    }

    public Map<String, Object> article(AccountAccess account, long id, boolean publish)
            throws SQLException {
        if (account == null || !account.isEnabled()) throw new PermissionErrorException();
        Map<String, Object> row = articles.get().loadById(id);
        if (row == null || !(row.get("userId") instanceof Number)
                || !account.canAccessArticle(((Number) row.get("userId")).intValue(), AccountAccess.truth(row.get("privacy"))))
            throw new PermissionErrorException();
        if (publish && !account.canPublish()) throw new PermissionErrorException();
        return row;
    }

    public void readArticle(AccountAccess account, long id) throws SQLException {
        article(account, id, false);
    }

    /** Shared declaration checks for HTTP features, assistants and background work. */
    public static void requireAction(AccountAccess account, String actionId) {
        for (AccountAction action : AccountAction.values()) {
            if (action.getId().equals(actionId)) {
                if (account == null || !action.allowed(account)) throw new PermissionErrorException();
                return;
            }
        }
        throw new IllegalArgumentException("Unknown account action: " + actionId);
    }

    public static void requireActions(AccountAccess account, java.util.Collection<String> actions) {
        actions.forEach(action -> requireAction(account, action));
    }

    public static boolean allowsActions(AccountAccess account, java.util.Collection<String> actions) {
        try { requireActions(account, actions); return true; }
        catch (PermissionErrorException denied) { return false; }
    }

    public void require(AccountAccess account, AccountAction action) {
        if (account == null || !action.allowed(account)) throw new PermissionErrorException();
    }
}
