package com.zrlog.admin.web.controller.api;

import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.data.security.AccountAction;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.CreateNavRequest;
import com.zrlog.admin.business.rest.request.UpdateNavRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.BlogNavService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.controller.BaseController;

import java.io.IOException;
import java.sql.SQLException;

public class BlogNavController extends BaseController {

    private final BlogNavService blogNavService = new BlogNavService();

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "navigation.delete")
    public DeleteResponse delete() throws SQLException {
        return new DeleteResponse(blogNavService.delete(getParamWithEmptyCheck("id")));
    }

    @ResponseBody
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "navigation.list")
    public AdminPageDataResponse<PageData<LogNavDTO>> index() throws SQLException {
        return new AdminPageDataResponse<>(blogNavService.find(), "", request.getUri());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "navigation.create")
    public UpdateRecordResponse add() throws IOException, SQLException {
        return new UpdateRecordResponse(blogNavService.add(getRequestBodyWithNullCheck(CreateNavRequest.class)));
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.SITE_CONFIGURE, descriptionKey = "navigation.update")
    public UpdateRecordResponse update() throws IOException, SQLException {
        return new UpdateRecordResponse(blogNavService.update(getRequestBodyWithNullCheck(UpdateNavRequest.class)));
    }
}
