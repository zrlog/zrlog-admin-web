package com.zrlog.admin.web.controller.api;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.hibegin.http.server.util.PathUtil;
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
import com.zrlog.business.template.HtmlTemplateProcessor;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.model.WebSite;
import com.zrlog.util.BlogBuildInfoUtil;
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
    public AdminApiPageDataStandardResponse<Void> apply() throws SQLException {
        AdminApiPageDataStandardResponse<Void> apiStandardResponse = new AdminApiPageDataStandardResponse<>();
        apiStandardResponse.setError(0);
        if (new WebSite().updateByKV("template", AdminTemplateUtils.loadTemplatePathByRequestInfo(this))) {
            Cookie cookie = new Cookie();
            cookie.setName("template");
            cookie.setValue("");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            getResponse().addCookie(cookie);
        }
        apiStandardResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("updateSuccess"));
        return apiStandardResponse;
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<Void> preview() {
        if (EnvKit.isFaaSMode()) {
            AdminApiPageDataStandardResponse<Void> apiStandardResponse = new AdminApiPageDataStandardResponse<>();
            apiStandardResponse.setError(1);
            apiStandardResponse.setMessage("Not support preview");
            return apiStandardResponse;
        }
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        Cookie cookie = new Cookie();
        cookie.setName("template");
        cookie.setValue(template);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        getResponse().addCookie(cookie);
        return new AdminApiPageDataStandardResponse<>();
    }

    @ResponseBody
    @RequestLock
    public DeleteResponse delete() {
        String shortTemplate = getParamWithEmptyCheck("shortTemplate");
        File file = PathUtil.safeAppendFilePath(PathUtil.getStaticPath() + Constants.TEMPLATE_BASE_PATH, shortTemplate);
        if (file.exists()) {
            return new DeleteResponse(FileUtils.deleteFile(file.toString()));
        }
        return new DeleteResponse(false);
    }

    @ResponseBody
    public UploadTemplateResponse upload() throws IOException {
        String uploadFieldName = "file";
        File templateName = request.getFile(uploadFieldName);
        if (Objects.isNull(templateName)) {
            throw new ArgsException("file");
        }
        return templateService.upload("", templateName);
    }

    @RefreshCache(updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequestLock
    public UpdateRecordResponse config() throws SQLException, IOException {
        UpdateTemplateConfigRequest param = getRequestBodyWithNullCheck(UpdateTemplateConfigRequest.class);
        String template = param.getTemplate();
        if (StringUtils.isNotEmpty(template)) {
            param.remove("template");
            return templateService.save(template, param);
        }
        return new UpdateRecordResponse();
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<TemplateVO> configParams() throws IOException {
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        TemplateVO templateVO = templateService.loadTemplateConfig(template);
        templateVO.getConfig().values().forEach((value) -> {
            if (Objects.equals(value.getContentType(), "html") && Objects.nonNull(value.getValue())) {
                if (value.getValue() instanceof String) {
                    value.setPreviewValue(previewValue((String) value.getValue()));
                }
            }
        });
        return new AdminApiPageDataStandardResponse<>(templateVO, "", request.getUri());
    }

    private String previewValue(String value) {
        HtmlTemplateProcessor htmlTemplateProcessor = new HtmlTemplateProcessor(request, null, "/");
        return htmlTemplateProcessor.transform(value);
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<TemplateValuePreviewResponse> previewConfigValue() {
        return new AdminApiPageDataStandardResponse<>(new TemplateValuePreviewResponse(previewValue(request.getParaToStr("value"))), "", request.getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<List<BaseTemplateVO>> index() throws IOException {
        return new AdminApiPageDataStandardResponse<>(templateService.getAllTemplates(TemplateHelper.getTemplatePath(getRequest())),
                "", request.getUri());
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<TemplateDownloadResponse> templateCenter() {
        String host = request.getParaToStr("host", "");
        if (StringUtils.isEmpty(host)) {
            String referer = request.getHeader("referer");
            if (StringUtils.isNotEmpty(referer)) {
                host = URI.create(referer).getAuthority();
            } else {
                host = getRequest().getHeader("Host");
            }
        }
        TemplateDownloadResponse downloadResponse = new TemplateDownloadResponse("https://store.zrlog.com/template/index.html?from=" + AdminTokenThreadLocal.getUserProtocol() + "://" + host + request.getContextPath() + AdminConstants.ADMIN_URI_BASE_PATH + "/template&v=" + BlogBuildInfoUtil.getVersion() + "&id=" + BlogBuildInfoUtil.getBuildId() + "&upgrade-v3=true");
        return new AdminApiPageDataStandardResponse<>(downloadResponse, "", request.getUri());
    }
}
