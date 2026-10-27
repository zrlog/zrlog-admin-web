package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.util.function.Supplier;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.UploadedFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Authenticated transport adapter; artifact installation belongs to plugin-core.
 */
public class PluginUploadController extends BaseController {
    private final Supplier<PluginCorePlugin> pluginCore;
    private final UploadService uploads;
    private final AdminRequestIdentity identity;

    @Inject
    public PluginUploadController(Supplier<PluginCorePlugin> pluginCore, UploadService uploads, AdminRequestIdentity identity) {
        this.pluginCore = pluginCore;
        this.uploads = uploads;
        this.identity = identity;
    }

    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.PLUGIN_MANAGE, descriptionKey = "plugin.upload")
    public void install() throws Exception {
        forward(new PluginInstallRequest(request, null, null));
    }

    private void forward(HttpRequest installRequest) throws Exception {
        if (!pluginCore.get()
                .accessPlugin("/api/install", installRequest, response, AdminTokenThreadLocal.getUser())) {
            response.renderCode(503);
        }
    }

    /** Legacy multipart uploads are stored by admin, then installed through the same URL flow. */
    @Deprecated
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.PLUGIN_MANAGE, descriptionKey = "plugin.upload")
    public void upload() throws Exception {
        UploadedFile file = request.getUploadedFile("file");
        if (file == null) { install(); return; }
        String source = uploads.saveUploadedFile(identity.current(), file, "plugins", null, true,
                request, AdminTokenThreadLocal.getUser()).getUrl();
        Map<String, String[]> form = new LinkedHashMap<>();
        form.put("source", new String[]{source});
        form.put("fileName", new String[]{request.getParaToStr("fileName", file.getOriginalFilename())});
        form.put("overwrite", new String[]{request.getParaToStr("overwrite", "false")});
        String encoded = form.entrySet().stream().map(entry -> entry.getKey() + "=" +
                URLEncoder.encode(entry.getValue()[0] == null ? "" : entry.getValue()[0], StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        forward(new PluginInstallRequest(request, form, encoded));
    }
}
