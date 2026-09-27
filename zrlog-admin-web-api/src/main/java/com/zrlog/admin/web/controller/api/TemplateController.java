package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.request.UpdateTemplateConfigRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.TemplateService;
import com.zrlog.admin.util.AdminTemplateUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.TemplateHelper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class TemplateController extends BaseController {


    private final TemplateService templateService = new TemplateService();


    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.apply")
    public ApiStandardResponse<Void> apply() throws SQLException {
        ApiStandardResponse<Void> apiStandardResponse = new ApiStandardResponse<>();
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        if (templateService.apply(template, request)) {
            Cookie cookie = new Cookie();
            cookie.setName("template");
            cookie.setValue("");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            getResponse().addCookie(cookie);
        }
        apiStandardResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.common.update.success"));
        return apiStandardResponse;
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.preview")
    public ApiStandardResponse<Void> preview() {
        if (EnvKit.isFaaSMode()) {
            ApiStandardResponse<Void> apiStandardResponse = new ApiStandardResponse<>();
            apiStandardResponse.setError(1);
            apiStandardResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.template.preview.error.unsupported"));
            return apiStandardResponse;
        }
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        Cookie cookie = new Cookie();
        cookie.setName("template");
        cookie.setValue(template);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        getResponse().addCookie(cookie);
        return new ApiStandardResponse<>();
    }

    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.delete")
    public DeleteResponse delete() {
        String shortTemplate = getParamWithEmptyCheck("shortTemplate");
        return templateService.delete(shortTemplate, request);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequestLock
    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.upload")
    public UploadTemplateResponse upload() throws IOException {
        String uploadFieldName = "file";
        File uploadFile = request.getFile(uploadFieldName);
        if (Objects.isNull(uploadFile)) {
            throw new ArgsException("file");
        }
        String shortTemplate = getParamWithEmptyCheck("shortTemplate");
        boolean overwrite = Boolean.parseBoolean(request.getParaToStr("overwrite", "false"));
        return templateService.uploadAndRecord(shortTemplate, overwrite, uploadFile, request);
    }

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.saveConfig")
    public UpdateRecordResponse config() throws SQLException, IOException {
        UpdateTemplateConfigRequest param = getRequestBodyWithNullCheck(UpdateTemplateConfigRequest.class);
        String template = param.getTemplate();
        if (StringUtils.isNotEmpty(template)) {
            param.remove("template");
            return templateService.saveAndRecord(template, param, request);
        }
        return new UpdateRecordResponse();
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.readConfig")
    public AdminPageDataResponse<TemplateVO> configParams() throws IOException {
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        return new AdminPageDataResponse<>(templateService.loadTemplateConfig(template, request), "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.previewValue")
    public ApiStandardResponse<TemplateValuePreviewResponse> previewConfigValue() {
        return new ApiStandardResponse<>(new TemplateValuePreviewResponse(
                templateService.previewValue(request.getParaToStr("value"), request)));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.list")
    public AdminPageDataResponse<List<TemplateEntryResponse>> index() throws IOException {
        return new AdminPageDataResponse<>(templateService.getAllTemplates(TemplateHelper.getTemplatePath(getRequest())),
                "", request.getUri());
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "template.catalog")
    public AdminPageDataResponse<TemplateDownloadResponse> templateCenter() {
        String host = request.getParaToStr("host", "");
        if (StringUtils.isEmpty(host)) {
            String referer = request.getHeader("referer");
            if (StringUtils.isNotEmpty(referer)) {
                host = URI.create(referer).getAuthority();
            } else {
                host = getRequest().getHeader("Host");
            }
        }
        TemplateDownloadResponse downloadResponse = templateService.templateCenter(
                AdminTokenThreadLocal.getUserProtocol(), host, request.getContextPath());
        return new AdminPageDataResponse<>(downloadResponse, "", request.getUri());
    }
}
