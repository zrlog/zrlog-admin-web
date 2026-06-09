package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminStaticSiteSyncResponse;
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdminStaticSiteController extends BaseController {

    @ResponseBody
    @RequestLock
    public void startSync() throws IOException {
        if (StaticSitePlugin.isDisabled()) {
            // 静态化未启用时，只通知浏览器刷新缓存状态。
            response.renderJson(new ApiStandardResponse<>(new AdminStaticSiteSyncResponse(true)));
            return;
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = Constants.zrLogConfig.getPlugin(AdminStaticResourcePlugin.class);
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        List<StaticSiteType> siteTypes = List.of(StaticSiteType.ADMIN);
        if (!isSseRequest()) {
            boolean synced = CacheUtils.refreshStaticSiteCache(request, siteTypes);
            new MessageCenterOperationService().recordStaticSiteSync(siteTypes, synced);
            new AdminAuditService().record(request, AdminAuditAction.SYNC_ADMIN_STATIC_SITE);
            response.renderJson(new ApiStandardResponse<>(new AdminStaticSiteSyncResponse(synced)));
            return;
        }
        AtomicBoolean synced = new AtomicBoolean(false);
        AdminStaticSiteSsePublisher.write(
                response,
                "admin-static-site-sync",
                siteTypes,
                emitter -> {
                },
                () -> synced.set(CacheUtils.refreshStaticSiteCache(request, siteTypes)),
                emitter -> {
                    new MessageCenterOperationService().recordStaticSiteSync(siteTypes, synced.get());
                    new AdminAuditService().record(request, AdminAuditAction.SYNC_ADMIN_STATIC_SITE);
                    emitter.send("response", new ApiStandardResponse<>(new AdminStaticSiteSyncResponse(synced.get())));
                }
        );
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    @ResponseBody
    public AdminPageDataResponse<AdminStaticSiteSyncResponse> index() {
        if (StaticSitePlugin.isDisabled()) {
            return new AdminPageDataResponse<>(new AdminStaticSiteSyncResponse(true), "", request.getUri());
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = Constants.zrLogConfig.getPlugin(AdminStaticResourcePlugin.class);
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        return new AdminPageDataResponse<>(new AdminStaticSiteSyncResponse(adminStaticResourcePlugin.isSynchronized(AdminTokenThreadLocal.getUserProtocol())), "", request.getUri());
    }
}
