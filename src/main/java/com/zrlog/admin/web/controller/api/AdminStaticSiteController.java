package com.zrlog.admin.web.controller.api;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.AdminStaticSiteSyncResponse;
import com.zrlog.admin.util.AdminStaticSiteSsePublisher;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.plugin.AdminStaticResourcePlugin;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.CacheUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdminStaticSiteController extends BaseController {

    @ResponseBody
    @RequestLock
    public void startSync() throws IOException {
        if (StaticSitePlugin.isDisabled()) {
            //仅让浏览器刷新
            response.renderJson(new AdminApiPageDataStandardResponse<>(new AdminStaticSiteSyncResponse(true)));
            return;
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = Constants.zrLogConfig.getPlugin(AdminStaticResourcePlugin.class);
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        List<StaticSiteType> siteTypes = List.of(StaticSiteType.ADMIN);
        if (!isSseRequest()) {
            response.renderJson(new AdminApiPageDataStandardResponse<>(new AdminStaticSiteSyncResponse(CacheUtils.refreshStaticSiteCache(request, siteTypes))));
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
                emitter -> emitter.send("response", new AdminApiPageDataStandardResponse<>(new AdminStaticSiteSyncResponse(synced.get())))
        );
    }

    private boolean isSseRequest() {
        String accept = request.getHeader("Accept");
        return Objects.nonNull(accept) && accept.contains("text/event-stream");
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<AdminStaticSiteSyncResponse> index() {
        if (StaticSitePlugin.isDisabled()) {
            return new AdminApiPageDataStandardResponse<>(new AdminStaticSiteSyncResponse(true), "", request.getUri());
        }
        AdminStaticResourcePlugin adminStaticResourcePlugin = Constants.zrLogConfig.getPlugin(AdminStaticResourcePlugin.class);
        if (Objects.isNull(adminStaticResourcePlugin)) {
            throw new NotFindResourceException("plugin not found");
        }
        return new AdminApiPageDataStandardResponse<>(new AdminStaticSiteSyncResponse(adminStaticResourcePlugin.isSynchronized(AdminTokenThreadLocal.getUserProtocol())), "", request.getUri());
    }
}
