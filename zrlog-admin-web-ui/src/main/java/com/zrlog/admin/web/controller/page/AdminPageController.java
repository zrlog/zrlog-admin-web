package com.zrlog.admin.web.controller.page;

import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ServerSideDataResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminPageService;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.data.security.AccountAction;
import com.zrlog.plugin.BaseStaticSitePlugin;

import java.io.InputStream;
import java.util.Objects;

public class AdminPageController extends Controller {
    private final AdminPageDataFactory pages;


    private final AdminPageService adminPageService;
    private final TokenService tokenService;

    public AdminPageController(AdminPageService adminPageService, TokenService tokenService, AdminPageDataFactory pages) {
        this.pages = pages;
        this.tokenService = tokenService;
        this.adminPageService = adminPageService;
    }

    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.page")
    public void index() throws Throwable {
        if (BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            renderIndex();
            return;
        }
        if (Objects.equals(request.getUri(), Constants.ADMIN_URI_BASE_PATH) || Objects.equals(request.getUri(), Constants.ADMIN_URI_BASE_PATH + "/")) {
            response.redirect(Constants.ADMIN_URI_BASE_PATH + AdminConstants.INDEX_URI_PATH);
            return;
        }
        if (Objects.equals(request.getUri(), Constants.ADMIN_URI_BASE_PATH + "/website/version")
                || Objects.equals(request.getUri(), Constants.ADMIN_URI_BASE_PATH + "/website/upgrade")) {
            String query = request.getQueryStr();
            response.redirect(request.getUri().replace("/website/", "/system/")
                    + (query == null || query.isEmpty() ? "" : "?" + query));
            return;
        }
        renderIndex();
    }

    private void renderIndex() throws Throwable {
        InputStream inputStream = AdminPageController.class.getResourceAsStream(AdminConstants.ADMIN_HTML_PAGE);
        if (Objects.isNull(inputStream)) {
            response.renderCode(404);
            return;
        }
        String html = adminPageService.buildHtml(request, response, inputStream);
        response.renderHtmlStr(html);
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "admin.pageData")
    public AdminPageDataResponse<ServerSideDataResponse<Object>> ssJson() throws Throwable {
        return pages.response(adminPageService.serverSide(request.getParaToStr("uri"), request, response));
    }

    @RequiresAction(value = AccountAction.SESSION, descriptionKey = "account.logout")
    public void logout() {
        tokenService.removeAdminToken(request, response);
    }
}
