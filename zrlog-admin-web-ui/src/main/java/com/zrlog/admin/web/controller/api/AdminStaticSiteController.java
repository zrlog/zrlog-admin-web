package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.admin.web.audit.AdminAuditContexts;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminStaticSiteSyncResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AdminStaticSiteController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final AdminAuditService adminAuditService;
    private final MessageCenterOperationService messageCenterOperationService;

    private final AdminPageDataFactory pages;

    private final CacheRefreshService cacheRefresh;
    private final AdminStaticSiteSsePublisher staticSitePublisher;
    private final Supplier<AdminStaticResourcePlugin> staticSite;

    @Inject
    public AdminStaticSiteController(
            CacheRefreshService cacheRefresh,
            AdminStaticSiteSsePublisher staticSitePublisher,
            Supplier<AdminStaticResourcePlugin> staticSite,
            AdminPageDataFactory pages,
            AdminAuditService adminAuditService,
            MessageCenterOperationService messageCenterOperationService,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminAuditService = adminAuditService;
        this.messageCenterOperationService = messageCenterOperationService;

        this.pages = pages;
        this.cacheRefresh = cacheRefresh;
        this.staticSitePublisher = staticSitePublisher;
        this.staticSite = staticSite;
    }


    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.syncAdmin")
    public void startSync() throws IOException {
        if (cacheRefresh.isStaticSiteDisabled()) {
            // 静态化未启用时，只通知浏览器刷新缓存状态。
            response.renderJson(new ApiStandardResponse<>(new AdminStaticSiteSyncResponse(true)));
            return;
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = staticSite.get();
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        List<StaticSiteType> siteTypes = List.of(StaticSiteType.ADMIN);
        if (!isSseRequest()) {
            boolean synced = cacheRefresh.refreshStaticSiteCache(request, siteTypes);
            this.messageCenterOperationService.recordStaticSiteSync(siteTypes, synced);
            this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.SYNC_ADMIN_STATIC_SITE);
            response.renderJson(new ApiStandardResponse<>(new AdminStaticSiteSyncResponse(synced)));
            return;
        }
        AtomicBoolean synced = new AtomicBoolean(false);
        staticSitePublisher.write(
                response,
                "admin-static-site-sync",
                siteTypes,
                emitter -> {},
                () -> synced.set(cacheRefresh.refreshStaticSiteCache(request, siteTypes)),
                emitter -> {
                    this.messageCenterOperationService.recordStaticSiteSync(
                            siteTypes, synced.get());
                    this.adminAuditService.record(AdminAuditContexts.from(request, AdminTokenThreadLocal.getUserId()), AdminAuditAction.SYNC_ADMIN_STATIC_SITE);
                    emitter.send(
                            "response",
                            new ApiStandardResponse<>(
                                    new AdminStaticSiteSyncResponse(synced.get())));
                });
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.adminSyncStatus")
    public AdminPageDataResponse<AdminStaticSiteSyncResponse> index() {
        if (cacheRefresh.isStaticSiteDisabled()) {
            return pages.response(requestIdentity.optional(), new AdminStaticSiteSyncResponse(true), "", request.getUri());
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = staticSite.get();
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        return pages.response(requestIdentity.optional(), new AdminStaticSiteSyncResponse(adminStaticResourcePlugin.isSynchronized(AdminTokenThreadLocal.getUserProtocol())), "", request.getUri());
    }
}
