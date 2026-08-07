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
import com.zrlog.admin.business.service.AdminAuditService;
import com.zrlog.admin.business.service.TemplateService;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.admin.util.AdminTemplateUtils;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.template.HtmlTemplateProcessor;
import com.zrlog.common.Constants;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.rest.response.ApiStandardResponse;
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
    public ApiStandardResponse<Void> apply() throws SQLException {
        ApiStandardResponse<Void> apiStandardResponse = new ApiStandardResponse<>();
        apiStandardResponse.setError(0);
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        if (new WebSite().updateByKV("template", template)) {
            Cookie cookie = new Cookie();
            cookie.setName("template");
            cookie.setValue("");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            getResponse().addCookie(cookie);
        }
        new AdminAuditService().record(request, AdminAuditAction.APPLY_TEMPLATE, template);
        apiStandardResponse.setMessage(I18nUtil.getAdminBackendStringFromRes("admin.common.update.success"));
        return apiStandardResponse;
    }

    @ResponseBody
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
    public DeleteResponse delete() {
        String shortTemplate = getParamWithEmptyCheck("shortTemplate");
        File file = PathUtil.safeAppendFilePath(PathUtil.getStaticPath() + Constants.TEMPLATE_BASE_PATH, shortTemplate);
        if (file.exists()) {
            boolean deleted = FileUtils.deleteFile(file.toString());
            if (deleted) {
                new AdminAuditService().record(request, AdminAuditAction.DELETE_TEMPLATE, shortTemplate);
            }
            return new DeleteResponse(deleted);
        }
        return new DeleteResponse(false);
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.POST)
    @RequestLock
    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    public UploadTemplateResponse upload() throws IOException {
        String uploadFieldName = "file";
        File uploadFile = request.getFile(uploadFieldName);
        if (Objects.isNull(uploadFile)) {
            throw new ArgsException("file");
        }
        String shortTemplate = getParamWithEmptyCheck("shortTemplate");
        boolean overwrite = Boolean.parseBoolean(request.getParaToStr("overwrite", "false"));
        UploadTemplateResponse response = templateService.upload(shortTemplate, overwrite, uploadFile);
        if (response.getError() == 0 && Objects.nonNull(response.getData())) {
            new AdminAuditService().record(request, AdminAuditAction.UPLOAD_TEMPLATE,
                    response.getData().getShortTemplate());
        }
        return response;
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
            UpdateRecordResponse response = templateService.save(template, param);
            new AdminAuditService().record(request, AdminAuditAction.UPDATE_TEMPLATE_CONFIG, template);
            return response;
        }
        return new UpdateRecordResponse();
    }

    @ResponseBody
    public AdminPageDataResponse<TemplateVO> configParams() throws IOException {
        String template = AdminTemplateUtils.loadTemplatePathByRequestInfo(this);
        TemplateVO templateVO = templateService.loadTemplateConfig(template);
        templateVO.getConfig().values().forEach((value) -> {
            if (Objects.equals(value.getContentType(), "html") && Objects.nonNull(value.getValue())) {
                if (value.getValue() instanceof String) {
                    value.setPreviewValue(previewValue((String) value.getValue()));
                }
            }
        });
        return new AdminPageDataResponse<>(templateVO, "", request.getUri());
    }

    private String previewValue(String value) {
        HtmlTemplateProcessor htmlTemplateProcessor = new HtmlTemplateProcessor(request, null, "/");
        return htmlTemplateProcessor.transform(value);
    }

    @ResponseBody
    public ApiStandardResponse<TemplateValuePreviewResponse> previewConfigValue() {
        return new ApiStandardResponse<>(new TemplateValuePreviewResponse(previewValue(request.getParaToStr("value"))));
    }

    @ResponseBody
    public AdminPageDataResponse<List<BaseTemplateVO>> index() throws IOException {
        return new AdminPageDataResponse<>(templateService.getAllTemplates(TemplateHelper.getTemplatePath(getRequest())),
                "", request.getUri());
    }

    @ResponseBody
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
        TemplateDownloadResponse downloadResponse = new TemplateDownloadResponse("https://store.zrlog.com/template/index.html?from=" + AdminTokenThreadLocal.getUserProtocol() + "://" + host + request.getContextPath() + AdminConstants.ADMIN_URI_BASE_PATH + "/template&v=" + BlogBuildInfoUtil.getVersion() + "&id=" + BlogBuildInfoUtil.getBuildId() + "&upgrade-v3=true");
        return new AdminPageDataResponse<>(downloadResponse, "", request.getUri());
    }
}
