package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;

import java.util.function.Supplier;

/** Authenticated transport adapter; artifact installation belongs to plugin-core. */
public class PluginUploadController extends BaseController {
    private final Supplier<PluginCorePlugin> pluginCore;

    public PluginUploadController(Supplier<PluginCorePlugin> pluginCore) {
        this.pluginCore = pluginCore;
    }

    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.PLUGIN_MANAGE, descriptionKey = "plugin.upload")
    public void upload() throws Exception {
        if (!pluginCore.get()
                .accessPlugin("/api/upload", request, response, AdminTokenThreadLocal.getUser())) {
            response.renderCode(503);
        }
    }
}
