package com.zrlog.admin.web.di;

import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.business.service.WebsiteKvService;

import dagger.Module;
import dagger.Provides;

import java.util.function.Supplier;

@Module
public final class AdminModule {
    private AdminModule() {}

    @Provides
    static AdminArticleService articles(Supplier<ContentComponent> content) {
        return content.get().articles();
    }

    @Provides
    static Supplier<String> staticResourceHost(WebsiteKvService settings) {
        return () -> settings.getString("admin_static_resource_base_url");
    }
}
