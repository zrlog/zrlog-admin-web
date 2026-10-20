package com.zrlog.admin.web.di.account;

import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.security.AdminRouteAuthorizer;
import dagger.Module;
import dagger.Provides;

/**
 * Shares account-owned services with other admin feature graphs.
 */
@Module
public final class AccountBindingsModule {
    private AccountBindingsModule() {
    }

    @Provides
    static AccountIdentityService identities(AccountComponent account) {
        return account.identities();
    }

    @Provides
    static AdminRequestIdentity requestIdentity(AccountComponent account) {
        return account.requestIdentity();
    }

    @Provides
    static AdminRouteAuthorizer routeAuthorizer(AccountComponent account) {
        return account.routeAuthorizer();
    }

    @Provides
    static AccountPermissionService permissions(AccountComponent account) {
        return account.permissions();
    }

    @Provides
    static UserPreferenceService preferences(AccountComponent account) {
        return account.preferences();
    }

    @Provides
    static UserService users(AccountComponent account) {
        return account.users();
    }

    @Provides
    static PasskeyService passkeys(AccountComponent account) {
        return account.passkeys();
    }

    @Provides
    static MfaService mfa(AccountComponent account) {
        return account.mfa();
    }

    @Provides
    static AdminPageDataFactory pages(AccountComponent account) {
        return account.pages();
    }

    @Provides
    static BackendServerUrl backend(AccountComponent account) {
        return account.backend();
    }

    @Provides
    static SecurityStore security(AccountComponent account) {
        return account.security();
    }
}
