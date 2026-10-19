package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.SwsBindings;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.account.AvatarStorage;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.web.controller.api.*;
import com.zrlog.admin.web.controller.api.AccessController;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class AccountWebSetup implements com.zrlog.web.WebSetup {
    private final Router router;
    private final SwsBindings bindings;
    private final BooleanSupplier installed;
    private final Supplier<AvatarStorage> avatarStorage;
    private final Supplier<java.util.Set<String>> cacheableApiUris;
    public AccountWebSetup(ServerConfig config, BooleanSupplier installed, Supplier<AvatarStorage> avatarStorage, Supplier<java.util.Set<String>> cacheableApiUris) {
        this.installed = installed;
        this.cacheableApiUris = cacheableApiUris;
        this.avatarStorage = avatarStorage;
        this.router = config.getRouter();
        this.bindings = config.getBindings();
    }
    @Override public void setup() {
        bindings.singleton(UserService.class, r -> new UserService(cacheableApiUris))
                .singleton(com.zrlog.admin.business.service.PasskeyService.class, r -> new com.zrlog.admin.business.service.PasskeyService(
                        () -> r.get(CacheService.class).getPublicWebSiteInfo().getHost(), r.get(UserService.class)))
                .controller(AccessController.class, (r, request, response) -> new AccessController(r.get(AdminPageDataFactory.class)))
                .controller(MemberController.class, (r, request, response) -> new MemberController(r.get(com.zrlog.admin.util.BackendServerUrl.class), r.get(AdminPageDataFactory.class)))
                .controller(AccountSessionController.class, (r, request, response) -> new AccountSessionController(r.get(UserService.class), r.get(TokenService.class), installed, r.get(AdminPageDataFactory.class), r.get(com.zrlog.admin.business.service.PasskeyService.class)))
                .controller(AdminUserController.class, (r, request, response) -> new AdminUserController(r.get(UserService.class), r.get(CacheService.class), avatarStorage, r.get(AdminPageDataFactory.class), r.get(com.zrlog.admin.business.service.PasskeyService.class)));
        router.addMapper("/api/admin/access", AccessController.class);
        router.addMapper("/api/admin/members", MemberController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/passkey/authentication/options",
                AccountSessionController.class, "passkeyAuthenticationOptions");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/passkey/authentication/verify",
                AccountSessionController.class, "passkeyAuthenticationVerify");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/user", AdminUserController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security", AdminUserController.class);
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkeys",
                AdminUserController.class, "passkeys");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/registration/options",
                AdminUserController.class, "passkeyRegistrationOptions");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/registration/verify",
                AdminUserController.class, "passkeyRegistrationVerify");
        router.addMapper("/api" + AdminConstants.ADMIN_URI_BASE_PATH + "/account-security/passkey/remove",
                AdminUserController.class, "passkeyRemove");
        router.addMapper("/api/admin/login", AccountSessionController.class, "login");
        router.addMapper("/api/admin/passkeyAuthenticationOptions", AccountSessionController.class, "passkeyAuthenticationOptions");
        router.addMapper("/api/admin/passkeyAuthenticationVerify", AccountSessionController.class, "passkeyAuthenticationVerify");
    }
}
