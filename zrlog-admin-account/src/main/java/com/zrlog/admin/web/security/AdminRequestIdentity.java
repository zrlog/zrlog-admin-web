package com.zrlog.admin.web.security;

import com.zrlog.admin.business.security.DelegatedAccess;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Adapts browser/Bearer request context to an explicitly authenticated account.
 */
@Singleton
public final class AdminRequestIdentity {
    private final AccountIdentityService identities;

    @Inject
    public AdminRequestIdentity(AccountIdentityService identities) {
        this.identities = identities;
    }

    public AccountAccess current() {
        return account(AdminTokenThreadLocal.getUser());
    }

    public AccountAccess optional() {
        var token = AdminTokenThreadLocal.getUser();
        return token == null ? null : account(token);
    }

    public AccountAccess account(AdminTokenVO token) {
        return DelegatedAccess.restrict(identities.account(token));
    }
}
