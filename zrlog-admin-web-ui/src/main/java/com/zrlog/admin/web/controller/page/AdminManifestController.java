package com.zrlog.admin.web.controller.page;

import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminManifestResponse;
import com.zrlog.admin.util.ManifestUtils;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.function.Supplier;

public class AdminManifestController extends BaseController {
    private final Supplier<PublicWebSiteInfo> site;

    @Inject
    public AdminManifestController(Supplier<PublicWebSiteInfo> site) {
        this.site = site;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.manifest")
    public AdminManifestResponse manifest() throws IOException {
        return ManifestUtils.manifest(request, site.get());
    }
}
