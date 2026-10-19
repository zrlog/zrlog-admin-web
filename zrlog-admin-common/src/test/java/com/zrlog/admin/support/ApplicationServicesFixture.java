package com.zrlog.admin.support;

import com.zrlog.admin.util.AdminStaticSiteProgress;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.interceptor.AdminActionDispatcher;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.service.UpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.Collections;

/** Assembles explicit dependencies from a test's application, without a global resolver. */
public final class ApplicationServicesFixture {
    private ApplicationServicesFixture() { }

    public static java.util.function.Function<com.hibegin.http.server.api.HttpRequest, String> staticResourceUrl() {
        return request -> com.zrlog.admin.util.AdminWebTools.getAdminStaticResourceBaseUrl(request,
                () -> new com.zrlog.model.WebSite().getStringValueByName("admin_static_resource_base_url"));
    }

    public static java.util.function.Supplier<String> siteHost(ZrLogConfig config) {
        var source = site(config);
        return () -> source.get() == null ? "" : source.get().getHost();
    }

    public static java.util.function.Supplier<String> siteTitle(ZrLogConfig config) {
        var source = site(config);
        return () -> source.get() == null ? "" : source.get().getTitle();
    }

    public static java.util.function.Supplier<PublicWebSiteInfo> site(ZrLogConfig config) {
        return () -> {
            var provider = config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
            return provider == null ? null : provider.getPublicWebSiteInfo();
        };
    }

    public static java.util.function.Supplier<String> buildId(com.zrlog.admin.business.service.AdminResource resources) {
        return () -> resources == null ? "" : resources.getStaticResourceBuildId();
    }

    public static com.zrlog.admin.util.BackendServerUrl backend(ZrLogConfig config) {
        return new com.zrlog.admin.util.BackendServerUrl(
                () -> config == null ? null : config.getServerConfig().getPort(),
                () -> config == null ? "" : config.getServerConfig().getContextPath(), () -> {
                    var provider = config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
                    var site = provider == null ? null : provider.getPublicWebSiteInfo();
                    return site == null ? "" : site.getHost();
                });
    }

    public static <T> java.util.function.BooleanSupplier available(ZrLogConfig config, Class<T> type) {
        var capability = capability(config, type);
        return () -> capability.get() != null;
    }

    public static <T> java.util.function.Supplier<T> capability(ZrLogConfig config, Class<T> type) {
        return () -> config == null ? null : config.getWebSetup(type);
    }

    public static java.util.function.BooleanSupplier installed(ZrLogConfig config) {
        return () -> config != null && config.isInstalled();
    }

    public static UpgradeNoticeService upgradeNotices(ZrLogConfig config) {
        return new UpgradeNoticeService(() -> config != null && config.isInstalled());
    }

    public static UpgradeService upgrades(ZrLogConfig config) {
        return new UpgradeService(config == null ? null : config.getUpdater(), upgradeNotices(config));
    }

    public static CacheRefreshService cacheRefresh(CacheService cache, ZrLogConfig config) {
        CacheService source = cache == null ? new InMemoryZrLogDatabase.TestCacheService() {
            @Override public PublicWebSiteInfo getPublicWebSiteInfo() {
                WebSiteInfoProvider provider = config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
                return provider == null ? super.getPublicWebSiteInfo() : provider.getPublicWebSiteInfo();
            }
        } : cache;
        return new CacheRefreshService(source, () -> config == null ? null : config.getPlugin(PluginCorePlugin.class),
                () -> config == null ? Collections.emptyList() : config.getPluginsByClazz(StaticSitePlugin.class));
    }

    public static AdminStaticSiteSsePublisher publisher(CacheService cache, ZrLogConfig config) {
        CacheRefreshService refresh = cacheRefresh(cache, config);
        return new AdminStaticSiteSsePublisher(refresh::isStaticSiteDisabled, new AdminStaticSiteProgress(
                () -> config == null ? Collections.emptyList() : config.getPluginsByClazz(StaticSitePlugin.class)));
    }

    public static AdminActionDispatcher dispatcher(CacheService cache, ZrLogConfig config) {
        return new AdminActionDispatcher(cacheRefresh(cache, config), publisher(cache, config));
    }
}
