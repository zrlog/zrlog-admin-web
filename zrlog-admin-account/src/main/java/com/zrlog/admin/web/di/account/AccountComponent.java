package com.zrlog.admin.web.di.account;

import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.security.SecurityStore;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.business.di.ServiceBindingsModule;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Component(modules = {DataBindingsModule.class, ServiceBindingsModule.class, AccountModule.class})
public interface AccountComponent {
    TokenService tokens();

    UserService users();

    AccountProfileService profiles();

    PasskeyService passkeys();

    MfaService mfa();

    UserPreferenceService preferences();

    AccountPermissionService permissions();

    AdminPageDataFactory pages();

    BackendServerUrl backend();

    SecurityStore security();

    MemberService members();

    AdminUserController userController();

    MemberController memberController();

    AccessController accessController();

    AccountSessionController sessionController();

    @Component.Factory
    interface Factory {
        AccountComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance ServiceComponent services,
                @BindsInstance BooleanSupplier installed,
                @BindsInstance Supplier<AvatarStorage> avatars,
                @BindsInstance Supplier<Set<String>> cacheableApiUris,
                @BindsInstance Supplier<PublicWebSiteInfo> site,
                @BindsInstance @Named("adminBuildId") Supplier<String> buildId,
                @BindsInstance Supplier<Integer> listenerPort,
                @BindsInstance @Named("contextPath") Supplier<String> contextPath);
    }
}
