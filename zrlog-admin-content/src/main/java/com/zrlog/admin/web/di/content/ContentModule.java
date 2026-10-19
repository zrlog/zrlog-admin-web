package com.zrlog.admin.web.di.content;

import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.common.CacheService;
import com.zrlog.common.vo.PublicWebSiteInfo;

import dagger.Module;
import dagger.Provides;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Module
public final class ContentModule {
    private ContentModule() {}

    @Provides
    static Supplier<PublicWebSiteInfo> site(Provider<CacheService> cache) {
        return () -> cache.get().getPublicWebSiteInfo();
    }

    @Provides
    @Named("backendUrl")
    static Supplier<String> backend(BackendServerUrl backend) {
        return backend::configured;
    }

    @Provides
    static BooleanSupplier staticHtml(Provider<CacheService> cache) {
        return () ->
                Boolean.TRUE.equals(cache.get().getPublicWebSiteInfo().getGenerator_html_status());
    }
}
