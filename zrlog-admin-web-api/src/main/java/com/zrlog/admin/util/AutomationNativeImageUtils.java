package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.automation.*;

import java.util.List;

public final class AutomationNativeImageUtils {
    private AutomationNativeImageUtils() { }

    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(List.of(
                com.zrlog.business.plugin.PluginSchedulerTickResult.class,
                com.zrlog.business.plugin.PluginCacheRefreshResult.class,
                com.zrlog.business.plugin.PluginViewRenderResult.class,
                com.zrlog.business.plugin.PluginSchedulerTickResult.Child.class,
                AutomationViews.Task.class,
                AutomationViews.Overview.class,
                AutomationViews.Runs.class,
                AutomationViews.Run.class,
                AutomationViews.Settings.class,
                AutomationViews.Configuration.class,
                AutomationViews.Change.class,
                AutomationViews.SettingsChange.class,
                StoredTask.class,
                AutomationDefaults.State.class,
                AutomaticUpgradeTaskProvider.Receipt.class,
                AutomationSettingsService.Settings.class,
                AutomationSettingsService.SettingsView.class,
                AutomationSettingsService.IssuedToken.class,
                AutomationTickService.Health.class));
    }
}
