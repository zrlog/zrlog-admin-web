package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.TagManageRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.TagManagementEntryResponse;
import com.zrlog.admin.business.rest.response.TagManagementPreviewResponse;
import com.zrlog.admin.business.service.TagManagementService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.ZrLogUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

public class AdminTagController extends BaseController {

    private static final Set<String> SUPPORTED_OPERATIONS = Set.of("rename", "merge", "delete");

    private final TagManagementService tagManagementService = new TagManagementService();

    @ResponseBody
    public AdminPageDataResponse<PageData<TagManagementEntryResponse>> index() throws SQLException {
        String key = request.getParaToStr("key", "");
        PageData<TagManagementEntryResponse> data = tagManagementService.find(
                ZrLogUtil.getHomeUrlWithHost(request),
                ControllerUtil.getPageRequest(this),
                key);
        return new AdminPageDataResponse<>(data, "", request.getUri());
    }

    @ResponseBody
    public ApiStandardResponse<TagManagementPreviewResponse> preview() throws IOException, SQLException {
        TagManageRequest requestBody = getRequestBodyWithNullCheck(TagManageRequest.class);
        String operation = request.getParaToStr("operation", "rename");
        validateOperation(operation);
        validateTargetTag(requestBody, operation);
        return new ApiStandardResponse<>(tagManagementService.preview(requestBody, operation));
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public ApiStandardResponse<TagManagementPreviewResponse> rename() throws IOException, SQLException {
        TagManageRequest requestBody = getRequestBodyWithNullCheck(TagManageRequest.class);
        validateOperation("rename");
        validateTargetTag(requestBody, "rename");
        return new ApiStandardResponse<>(tagManagementService.execute(requestBody, "rename"));
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public ApiStandardResponse<TagManagementPreviewResponse> merge() throws IOException, SQLException {
        TagManageRequest requestBody = getRequestBodyWithNullCheck(TagManageRequest.class);
        validateOperation("merge");
        validateTargetTag(requestBody, "merge");
        return new ApiStandardResponse<>(tagManagementService.execute(requestBody, "merge"));
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public ApiStandardResponse<TagManagementPreviewResponse> delete() throws IOException, SQLException {
        TagManageRequest requestBody = getRequestBodyWithNullCheck(TagManageRequest.class);
        validateOperation("delete");
        return new ApiStandardResponse<>(tagManagementService.execute(requestBody, "delete"));
    }

    private void validateOperation(String operation) {
        if (!SUPPORTED_OPERATIONS.contains(operation)) {
            throw new ArgsException("operation");
        }
    }

    private void validateTargetTag(TagManageRequest requestBody, String operation) {
        if (!"delete".equals(operation) && StringUtils.isEmpty(requestBody.getTargetTag())) {
            throw new ArgsException("targetTag");
        }
        if (!"delete".equals(operation) && requestBody.getSourceTag().equals(requestBody.getTargetTag())) {
            throw new ArgsException("targetTag");
        }
    }
}
