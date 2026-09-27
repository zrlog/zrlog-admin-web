package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.rest.response.LinkPreviewResponse;
import com.zrlog.admin.business.service.LinkPreviewService;
import com.zrlog.common.rest.response.ApiStandardResponse;

public class LinkPreviewController extends Controller {

    private final LinkPreviewService linkPreviewService = new LinkPreviewService();

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "link.preview")
    public ApiStandardResponse<LinkPreviewResponse> index() {
        return new ApiStandardResponse<>(linkPreviewService.fetch(request.getParaToStr("url", ""),
                request.getHeader("User-Agent")));
    }
}
