package com.zrlog.admin.util;

import com.zrlog.admin.business.rest.response.StaticSiteProgressResponse;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdminStaticSiteProgress {

    public static StaticSiteProgressResponse snapshot(List<StaticSiteType> siteTypes) {
        int total = 0;
        int handled = 0;
        int handing = 0;
        int pending = 0;
        int retrying = 0;
        for (StaticSiteType siteType : siteTypes) {
            StaticSiteProgressResponse snapshot = snapshot(siteType);
            total += snapshot.getTotal();
            handled += snapshot.getHandled();
            handing += snapshot.getHanding();
            pending += snapshot.getPending();
            retrying += snapshot.getRetrying();
        }
        return response(total, handled, handing, pending, retrying,
                siteTypes.stream().map(Enum::name).collect(Collectors.toList()));
    }

    public static StaticSiteProgressResponse snapshot(StaticSiteType siteType) {
        int total = 0;
        int handled = 0;
        int handing = 0;
        int pending = 0;
        int retrying = 0;
        for (StaticSitePlugin plugin : Constants.zrLogConfig.getPluginsByClazz(StaticSitePlugin.class)) {
            if (!Objects.equals(plugin.getType(), siteType)) {
                continue;
            }
            for (StaticSitePlugin.HandleState state : plugin.getHandleStatusPageMap().values()) {
                total++;
                if (state == StaticSitePlugin.HandleState.HANDLED) {
                    handled++;
                } else if (state == StaticSitePlugin.HandleState.HANDING) {
                    handing++;
                } else if (state == StaticSitePlugin.HandleState.RE_FETCH) {
                    retrying++;
                } else {
                    pending++;
                }
            }
        }
        return response(total, handled, handing, pending, retrying, List.of(siteType.name()));
    }

    static StaticSiteProgressResponse response(int total, int handled, int handing, int pending, int retrying,
                                               List<String> siteTypes) {
        return new StaticSiteProgressResponse(total, handled, handing, pending, retrying, siteTypes);
    }
}
