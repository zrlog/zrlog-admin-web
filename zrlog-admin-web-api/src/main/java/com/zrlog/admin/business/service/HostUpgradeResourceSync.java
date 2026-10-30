package com.zrlog.admin.business.service;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.util.HttpRequestBuilder;
import com.zrlog.admin.business.automation.TaskAuthority;
import com.zrlog.admin.business.automation.TaskExecutionContext;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.data.security.AccountAction;

import java.net.URI;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Uses the same static generator and plugin delivery as the browser, within the owning tick. */
public final class HostUpgradeResourceSync {
    private final CacheRefreshService refresh;
    private final Supplier<List<StaticSitePlugin>> sites;
    private final BooleanSupplier hasUi;
    private final RequestFactory requests;
    private final AdminAuditService audit;

    public HostUpgradeResourceSync(CacheRefreshService refresh, Supplier<List<StaticSitePlugin>> sites,
                                   BooleanSupplier hasUi, RequestFactory requests, AdminAuditService audit) {
        this.refresh = refresh;
        this.sites = sites;
        this.hasUi = hasUi;
        this.requests = requests;
        this.audit = audit;
    }

    public void synchronize(TaskExecutionContext context) throws Exception {
        context.checkActive();
        TaskAuthority.require(context.actor(), AccountAction.SYSTEM_MANAGE.getId());
        if (!hasUi.getAsBoolean() || refresh.isStaticSiteDisabled()) return;
        if (sites.get().stream().noneMatch(site -> site.getType() == StaticSiteType.ADMIN))
            throw new IllegalStateException("Admin static generator is not ready");
        refresh.updateCacheWithinBudget(requests.create(), List.of(StaticSiteType.ADMIN),
                context.runId(), context.actor().getUserId(), context::remainingMillis, () -> {
                    try { context.checkActive(); }
                    catch (Exception error) { throw new IllegalStateException("automation.executionExpired", error); }
                });
        context.checkActive();
        audit.record(context.audit(), AdminAuditAction.SYNC_ADMIN_STATIC_SITE, "upgrade:" + context.runId());
    }

    /** A trigger may arrive at a different backend origin; verify the configured public static site. */
    public static HttpRequest request(String host, String resourceBaseUrl, RequestConfig config,
                                      ApplicationContext application) throws Exception {
        URI site = URI.create(BackendServerUrl.resolve("", host, ""));
        String scheme = site.getScheme();
        if (resourceBaseUrl != null && !resourceBaseUrl.isBlank()) {
            URI resources = URI.create(resourceBaseUrl);
            if (!("http".equals(resources.getScheme()) || "https".equals(resources.getScheme())) || resources.getHost() == null
                    || resources.getRawUserInfo() != null || resources.getRawQuery() != null
                    || resources.getRawFragment() != null) throw new IllegalArgumentException("admin_static_resource_base_url");
            int defaultPort = "https".equals(resources.getScheme()) ? 443 : 80;
            int resourcePort = resources.getPort() == -1 ? defaultPort : resources.getPort();
            int sitePort = site.getPort() == -1 ? defaultPort : site.getPort();
            if (resources.getHost().equalsIgnoreCase(site.getHost()) && resourcePort == sitePort) scheme = resources.getScheme();
        }
        config.setSsl("https".equals(scheme));
        return HttpRequestBuilder.buildRequest(HttpMethod.GET, application.getServerConfig().getContextPath() + "/",
                host, "ZrLog Assistant", config, application);
    }

    @FunctionalInterface
    public interface RequestFactory { HttpRequest create() throws Exception; }
}
