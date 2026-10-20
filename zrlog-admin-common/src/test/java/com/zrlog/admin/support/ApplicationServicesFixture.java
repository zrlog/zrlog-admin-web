package com.zrlog.admin.support;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.service.MessageCenterStateService;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.business.service.WebsiteCacheService;
import com.zrlog.admin.util.AdminStaticSiteProgress;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.util.AdminWebTools;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.interceptor.AdminActionDispatcher;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.BackupProtectionService;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.service.UpgradeService;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.WebSite;
import com.zrlog.web.WebSiteInfoProvider;

import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Assembles explicit dependencies from a test's application, without a global resolver. */
public final class ApplicationServicesFixture {
    private ApplicationServicesFixture() { }

    private static DataSourceWrapper database(ZrLogConfig config) {
        return config == null ? null : (DataSourceWrapper) config.getDataSource();
    }

    public static Function<com.hibegin.http.server.api.HttpRequest, String> staticResourceUrl(DataSourceWrapper source) {
        return request ->
                AdminWebTools.getAdminStaticResourceBaseUrl(
                        request,
                        () -> new WebSite(source).getStringValueByName("admin_static_resource_base_url"));
    }

    public static Supplier<String> siteHost(ZrLogConfig config) {
        var source = site(config);
        return () -> source.get() == null ? "" : source.get().getHost();
    }

    public static Supplier<String> siteTitle(ZrLogConfig config) {
        var source = site(config);
        return () -> source.get() == null ? "" : source.get().getTitle();
    }

    public static Supplier<PublicWebSiteInfo> site(ZrLogConfig config) {
        return () -> {
            var provider = config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
            return provider == null ? null : provider.getPublicWebSiteInfo();
        };
    }

    public static Supplier<String> buildId(AdminResource resources) {
        return () -> resources == null ? "" : resources.getStaticResourceBuildId();
    }

    public static BackendServerUrl backend(ZrLogConfig config) {
        return new BackendServerUrl(
                () -> config == null ? null : config.getServerConfig().getPort(),
                () -> config == null ? "" : config.getServerConfig().getContextPath(),
                () -> {
                    var provider =
                            config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
                    var site = provider == null ? null : provider.getPublicWebSiteInfo();
                    return site == null ? "" : site.getHost();
                },
                () -> new WebSite(database(config)));
    }

    public static <T> BooleanSupplier available(ZrLogConfig config, Class<T> type) {
        var capability = capability(config, type);
        return () -> capability.get() != null;
    }

    public static <T> Supplier<T> capability(ZrLogConfig config, Class<T> type) {
        return () -> config == null ? null : config.getWebSetup(type);
    }

    public static BooleanSupplier installed(ZrLogConfig config) {
        return () -> config != null && config.isInstalled();
    }

    public static UpgradeNoticeService upgradeNotices(ZrLogConfig config) {
        return new UpgradeNoticeService(installed(config), websiteKv(database(config)));
    }

    public static UpgradeService upgrades(ZrLogConfig config) {
        return new UpgradeService(() -> config == null ? null : config.getUpdater(), upgradeNotices(config), new BackupProtectionService(websiteKv(database(config))), websiteKv(database(config)));
    }

    public static CacheRefreshService cacheRefresh(CacheService cache, ZrLogConfig config) {
        CacheService source = cache == null ? new InMemoryZrLogDatabase.TestCacheService() {
            @Override public PublicWebSiteInfo getPublicWebSiteInfo() {
                WebSiteInfoProvider provider = config == null ? null : config.getWebSetup(WebSiteInfoProvider.class);
                return provider == null ? super.getPublicWebSiteInfo() : provider.getPublicWebSiteInfo();
            }
        } : cache;
        return new CacheRefreshService(source, () -> config == null ? null : config.getPlugin(PluginCorePlugin.class),
                () -> config == null ? Collections.emptyList() : config.getPluginsByClazz(StaticSitePlugin.class), () -> new WebSite(database(config)));
    }

    public static AdminStaticSiteSsePublisher publisher(CacheService cache, ZrLogConfig config) {
        CacheRefreshService refresh = cacheRefresh(cache, config);
        return new AdminStaticSiteSsePublisher(refresh::isStaticSiteDisabled, new AdminStaticSiteProgress(
                () -> config == null ? Collections.emptyList() : config.getPluginsByClazz(StaticSitePlugin.class)));
    }

    public static AdminActionDispatcher dispatcher(CacheService cache, ZrLogConfig config) {
        return new AdminActionDispatcher(
                cacheRefresh(cache, config),
                publisher(cache, config),
                ApplicationServicesFixture.locks(database(config)));
    }

    public static WebsiteKvService websiteKv(DataSourceWrapper source) {
        return new WebsiteKvService(() -> new WebSite(source));
    }

    public static WebsiteCacheService websiteCache(DataSourceWrapper source) {
        return new WebsiteCacheService(websiteKv(source));
    }

    public static AdminAuditService audit(DataSourceWrapper source) {
        return new AdminAuditService(websiteKv(source));
    }

    public static WebSiteService settings(DataSourceWrapper source) {
        return new WebSiteService(websiteKv(source), () -> new WebSite(source));
    }

    public static MessageCenterStateService messages(DataSourceWrapper source) {
        return new MessageCenterStateService(websiteCache(source), () -> new WebSite(source));
    }

    public static MessageCenterOperationService operations(DataSourceWrapper source) {
        return new MessageCenterOperationService(messages(source), websiteCache(source));
    }

    public static DistributedLock.Factory locks(DataSourceWrapper source) {
        return key -> new DistributedLock(() -> new WebSite(source), key);
    }
}
