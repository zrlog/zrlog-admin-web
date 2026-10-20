package com.zrlog.admin.web;

import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.di.SwsBindings;
import com.hibegin.http.server.web.Router;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.service.PasskeyService;
import com.zrlog.admin.business.service.UserService;
import com.zrlog.admin.web.controller.api.AccessController;
import com.zrlog.admin.web.controller.api.AccountSessionController;
import com.zrlog.admin.web.controller.api.AdminUserController;
import com.zrlog.admin.web.controller.api.MemberController;
import com.zrlog.admin.web.di.account.AccountComponent;
import com.zrlog.web.WebSetup;

public class AccountWebSetup implements WebSetup {
    private final Router router;
    private final SwsBindings bindings;

    public AccountWebSetup(ServerConfig config) {
        this.router = config.getRouter();
        this.bindings = config.getBindings();
    }

    @Override
    public void setup() {
        bindings.singleton(UserService.class, r -> r.get(AccountComponent.class).users())
                .singleton(PasskeyService.class, r -> r.get(AccountComponent.class).passkeys())
                .controller(
                        AccessController.class,
                        (r, request, response) -> r.get(AccountComponent.class).accessController())
                .controller(
                        MemberController.class,
                        (r, request, response) -> r.get(AccountComponent.class).memberController())
                .controller(
                        AccountSessionController.class,
                        (r, request, response) -> r.get(AccountComponent.class).sessionController())
                .controller(
                        AdminUserController.class,
                        (r, request, response) -> r.get(AccountComponent.class).userController());
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
