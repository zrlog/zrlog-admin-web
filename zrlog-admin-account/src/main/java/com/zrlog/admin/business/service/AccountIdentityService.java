package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.model.User;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;

/** Revalidates explicit credentials against the application's current account state. */
@Singleton
public final class AccountIdentityService {
    private final Provider<User> users;

    @Inject
    public AccountIdentityService(Provider<User> users) {
        this.users = users;
    }

    public AccountAccess account(AdminTokenVO token) {
        try {
            if (token == null) throw new PermissionErrorException();
            AccountAccess account = AccountAccess.from(users.get().loadById(token.getUserId()));
            if (!account.isEnabled() || token.getAuthVersion() != account.getAuthVersion()) {
                throw new PermissionErrorException();
            }
            return account;
        } catch (SQLException e) {
            throw new PermissionErrorException();
        }
    }

    /** Recheck a short-lived actor without discarding its immutable delegation restrictions. */
    public AccountAccess revalidate(AccountAccess actor) {
        try {
            if (actor == null || !actor.isEnabled()) throw new PermissionErrorException();
            AccountAccess current = AccountAccess.from(users.get().loadById(actor.getUserId()));
            if (!current.isEnabled() || current.getAuthVersion() != actor.getAuthVersion()
                    || !current.getRole().equals(actor.getRole())) {
                throw new PermissionErrorException();
            }
            return actor;
        } catch (SQLException e) {
            throw new PermissionErrorException();
        }
    }
}
