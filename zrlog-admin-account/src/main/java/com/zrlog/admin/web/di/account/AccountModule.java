package com.zrlog.admin.web.di.account;

import com.zrlog.common.vo.PublicWebSiteInfo;

import dagger.Module;
import dagger.Provides;

import jakarta.inject.Named;

import java.util.function.Supplier;

@Module
public final class AccountModule {
    private AccountModule() {}

    @Provides
    @Named("siteHost")
    static Supplier<String> siteHost(Supplier<PublicWebSiteInfo> site) {
        return () -> site.get() == null ? "" : site.get().getHost();
    }
}
