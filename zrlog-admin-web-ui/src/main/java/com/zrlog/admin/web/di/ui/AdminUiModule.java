package com.zrlog.admin.web.di.ui;

import com.zrlog.admin.business.service.*;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.di.AdminComponent;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.PublicWebSiteInfo;

import dagger.Module;
import dagger.Provides;

import jakarta.inject.Provider;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Module
public final class AdminUiModule {
    private AdminUiModule() {}

    @Provides
    static AdminStaticSiteSsePublisher publisher(Supplier<AdminStaticSiteSsePublisher> publisher) {
        return publisher.get();
    }

    @Provides
    static TemplateService templates(AdminComponent admin) {
        return admin.templates();
    }

    @Provides
    static AdminResourceService resources(AdminComponent admin) {
        return admin.resources();
    }

    @Provides
    static Supplier<TokenService> tokens(TokenService tokens) {
        return () -> tokens;
    }

    @Provides
    static Supplier<PublicWebSiteInfo> site(Provider<CacheService> cache) {
        return () -> cache.get().getPublicWebSiteInfo();
    }

    @Provides
    static BooleanSupplier staticHtml(Provider<CacheService> cache) {
        return () ->
                Boolean.TRUE.equals(cache.get().getPublicWebSiteInfo().getGenerator_html_status());
    }
}
