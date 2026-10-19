package com.zrlog.admin.web.di.account;

import com.zrlog.admin.web.token.AdminTokenService;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.util.WebSiteUtils;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import jakarta.inject.Named;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Module
public abstract class AccountModule {
    private AccountModule() {}

    @Binds
    abstract TokenService tokens(AdminTokenService tokens);

    @Provides
    @Named("adminSessionTimeoutMinutes")
    static long sessionTimeoutMinutes() {
        return WebSiteUtils.DEFAULT_SESSION_TIMEOUT / 60000;
    }

    @Provides
    @Named("adminStaticHtml")
    static BooleanSupplier staticHtml(Supplier<PublicWebSiteInfo> site) {
        return () -> {
            PublicWebSiteInfo info = site.get();
            return info != null && Boolean.TRUE.equals(info.getGenerator_html_status());
        };
    }

    @Provides
    @Named("siteHost")
    static Supplier<String> siteHost(Supplier<PublicWebSiteInfo> site) {
        return () -> site.get() == null ? "" : site.get().getHost();
    }
}
