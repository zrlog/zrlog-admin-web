package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.request.ProxySettingsRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.ProxySettingsResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.AdminProxyService;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.NetworkProxy;
import jakarta.inject.Inject;

import java.io.IOException;

public class AdminProxyController extends BaseController {
    private final AdminRequestIdentity requestIdentity;

    private final AdminProxyService adminProxyService;
    private final AdminPageDataFactory pages;

    @Inject
    public AdminProxyController(AdminPageDataFactory pages, AdminProxyService adminProxyService,
                                AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.adminProxyService = adminProxyService;

        this.pages = pages;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.proxy")
    public AdminPageDataResponse<ProxySettingsResponse> index() throws IOException {
        if (request.getMethod() == HttpMethod.POST) {
            ProxySettingsRequest input = getRequestBodyWithNullCheck(ProxySettingsRequest.class);
            try {
                NetworkProxy.save(input.getUrl(), input.getNoProxy());
            } catch (IllegalArgumentException error) {
                throw new ArgsException("proxy");
            }
        } else if (request.getMethod() != HttpMethod.GET) {
            response.renderCode(405);
            return null;
        }
        return pages.response(requestIdentity.optional(), new ProxySettingsResponse(NetworkProxy.initialize(), NetworkProxy.configured()),
                "", AdminConstants.ADMIN_URI_BASE_PATH + "/system/proxy");
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SYSTEM_MANAGE, descriptionKey = "system.testProxy")
    public ApiStandardResponse<Boolean> test() {
        if (request.getMethod() != HttpMethod.POST) {
            response.renderCode(405);
            return null;
        }
        ProxySettingsRequest input = getRequestBodyWithNullCheck(ProxySettingsRequest.class);
        try {
            return new ApiStandardResponse<>(
                    this.adminProxyService.testConnection(
                            NetworkProxy.preview(input.getUrl(), input.getNoProxy())));
        } catch (IllegalArgumentException error) {
            throw new ArgsException("proxy");
        }
    }
}
