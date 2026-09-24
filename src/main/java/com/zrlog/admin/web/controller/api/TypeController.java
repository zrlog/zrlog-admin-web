package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.CreateTypeRequest;
import com.zrlog.admin.business.rest.request.UpdateTypeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.ArticleTypeService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.controller.BaseController;
import com.zrlog.util.ZrLogUtil;

import java.io.IOException;
import java.sql.SQLException;

public class TypeController extends BaseController {

    private final ArticleTypeService articleTypeService = new ArticleTypeService();

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.TAXONOMY_MANAGE, descriptionKey = "category.delete")
    public DeleteResponse delete() throws SQLException {
        int typeId = Integer.parseInt(getParamWithEmptyCheck("id"));
        return new DeleteResponse(articleTypeService.delete(typeId));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.TAXONOMY_READ, descriptionKey = "category.list")
    public AdminPageDataResponse<PageData<TypeDTO>> index() throws SQLException {
        return new AdminPageDataResponse<>(articleTypeService.find(ZrLogUtil.getHomeUrlWithHost(request),
                ControllerUtil.unPageRequest(), Constants.isStaticHtmlStatus()), "", request.getUri());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.TAXONOMY_MANAGE, descriptionKey = "category.create")
    public UpdateRecordResponse add() throws IOException, SQLException {
        CreateTypeRequest requestBody = getRequestBodyWithNullCheck(CreateTypeRequest.class);
        return new UpdateRecordResponse(articleTypeService.add(requestBody));
    }


    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.TAXONOMY_MANAGE, descriptionKey = "category.update")
    public UpdateRecordResponse update() throws IOException, SQLException {
        UpdateTypeRequest requestBody = getRequestBodyWithNullCheck(UpdateTypeRequest.class);
        return new UpdateRecordResponse(articleTypeService.update(requestBody));
    }
}
