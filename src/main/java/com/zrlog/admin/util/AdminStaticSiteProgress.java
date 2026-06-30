package com.zrlog.admin.util;

import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AdminStaticSiteProgress {

    public static Map<String, Object> snapshot(List<StaticSiteType> siteTypes) {
        int total = 0;
        int handled = 0;
        int handing = 0;
        int pending = 0;
        int retrying = 0;
        for (StaticSiteType siteType : siteTypes) {
            Map<String, Object> snapshot = snapshot(siteType);
            total += (Integer) snapshot.get("total");
            handled += (Integer) snapshot.get("handled");
            handing += (Integer) snapshot.get("handing");
            pending += (Integer) snapshot.get("pending");
            retrying += (Integer) snapshot.get("retrying");
        }
        return toMap(total, handled, handing, pending, retrying, siteTypes.stream().map(Enum::name).collect(Collectors.toList()));
    }

    public static Map<String, Object> snapshot(StaticSiteType siteType) {
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
        return toMap(total, handled, handing, pending, retrying, List.of(siteType.name()));
    }

    static Map<String, Object> toMap(int total, int handled, int handing, int pending, int retrying, List<String> siteTypes) {
        return Map.of(
                "total", total,
                "handled", handled,
                "handing", handing,
                "pending", pending,
                "retrying", retrying,
                "siteTypes", siteTypes
        );
    }
}
