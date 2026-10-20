package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.CreateTypeRequest;
import com.zrlog.admin.business.rest.request.UpdateTypeRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.ArticleTypeService;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Supplier;

public class TypeController extends BaseController {
    private final AdminRequestIdentity requestIdentity;
    private final AdminPageDataFactory pages;
    private final Supplier<PublicWebSiteInfo> site;
    private final ArticleTypeService articleTypeService;

    @Inject
    public TypeController(
            AdminPageDataFactory pages,
            Supplier<PublicWebSiteInfo> site,
            ArticleTypeService articleTypeService,
            AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;
        this.articleTypeService = articleTypeService;

        this.pages = pages;
        this.site = site;
    }

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
    @RequestMethod(method = HttpMethod.GET)
    public AdminPageDataResponse<PageData<TypeDTO>> index() throws SQLException {
        PublicWebSiteInfo website = site.get();
        return pages.response(requestIdentity.optional(), articleTypeService.find(ZrLogUtil.getHomeUrlWithHost(request, website.getHost()),
                ControllerUtil.unPageRequest(), Boolean.TRUE.equals(website.getGenerator_html_status())), "", request.getUri());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.TAXONOMY_MANAGE, descriptionKey = "category.create")
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse add() throws IOException, SQLException {
        CreateTypeRequest requestBody = getRequestBodyWithNullCheck(CreateTypeRequest.class);
        return new UpdateRecordResponse(articleTypeService.add(requestBody));
    }


    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    @RequiresAction(value = AccountAction.TAXONOMY_MANAGE, descriptionKey = "category.update")
    @RequestMethod(method = HttpMethod.POST)
    public UpdateRecordResponse update() throws IOException, SQLException {
        UpdateTypeRequest requestBody = getRequestBodyWithNullCheck(UpdateTypeRequest.class);
        return new UpdateRecordResponse(articleTypeService.update(requestBody));
    }
}
