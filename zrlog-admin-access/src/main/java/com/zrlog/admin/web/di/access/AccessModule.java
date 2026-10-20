package com.zrlog.admin.web.di.access;

import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.util.BackendServerUrl;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Named;

import java.util.function.Supplier;

@Module
public final class AccessModule {
    private AccessModule() {
    }

    @Provides
    static Supplier<String> issuer(BackendServerUrl backend) {
        return backend::configured;
    }

    @Provides
    @Named("mcpIssuer")
    static Supplier<String> mcpIssuer(OAuthService oauth) {
        return oauth::issuer;
    }
}
