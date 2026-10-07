package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;

/** Authenticated transport adapter; artifact installation belongs to plugin-core. */
public class PluginUploadController extends BaseController {
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.PLUGIN_MANAGE, descriptionKey = "plugin.upload")
    public void upload() throws Exception {
        if (!Constants.zrLogConfig.getPlugin(PluginCorePlugin.class)
                .accessPlugin("/api/upload", request, response, AdminTokenThreadLocal.getUser())) {
            response.renderCode(503);
        }
    }
}
