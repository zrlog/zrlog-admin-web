package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.data.repository.AccountIdentityRepository;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.security.AccountAccess;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.sql.SQLException;

/** Revalidates explicit credentials against the application's current account state. */
@Singleton
public final class AccountIdentityService {
    private final Provider<AccountIdentityRepository> users;

    @Inject
    public AccountIdentityService(Provider<AccountIdentityRepository> users) {
        this.users = users;
    }

    public AccountAccess account(AdminTokenVO token) {
        if (token == null) throw new PermissionErrorException();
        AccountAccess account = loadAccount(token.getUserId());
        if (!account.isEnabled() || token.getAuthVersion() != account.getAuthVersion()) {
            throw new PermissionErrorException();
        }
        return account;
    }

    /** Recheck a short-lived actor without discarding its immutable delegation restrictions. */
    public AccountAccess revalidate(AccountAccess actor) {
        if (actor == null || !actor.isEnabled()) throw new PermissionErrorException();
        return requireUnchanged(actor, loadAccount(actor.getUserId()));
    }

    private AccountAccess loadAccount(int userId) {
        try {
            return users.get().accessById(userId);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Validate an account fetched by a combined database read, preserving delegation restrictions.
     */
    public static AccountAccess requireUnchanged(AccountAccess actor, AccountAccess current) {
        if (actor == null
                || !actor.isEnabled()
                || current == null
                || !current.isEnabled()
                || current.getUserId() != actor.getUserId()
                || current.getAuthVersion() != actor.getAuthVersion()
                || !current.getRole().equals(actor.getRole())) throw new PermissionErrorException();
        return actor;
    }
}
