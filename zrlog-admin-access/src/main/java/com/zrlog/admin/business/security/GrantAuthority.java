package com.zrlog.admin.business.security;

import com.zrlog.data.security.AccountAccess;

import java.util.Objects;

/**
 * Per-operation account and delegation ceiling supplied by the authenticated protocol boundary.
 */
public final class GrantAuthority {
    private final AccountAccess actor;
    private final boolean permitsInheritance;

    public GrantAuthority(AccountAccess actor, boolean permitsInheritance) {
        this.actor = Objects.requireNonNull(actor);
        this.permitsInheritance = permitsInheritance;
    }

    public AccountAccess actor() {
        return actor;
    }

    public boolean permitsInheritance() {
        return permitsInheritance;
    }
}
