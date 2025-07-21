package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.CreateLinkRequest;
import com.zrlog.admin.business.rest.request.UpdateLinkRequest;
import com.zrlog.admin.business.rest.response.AdminApiPageDataStandardResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.common.controller.BaseController;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.Link;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

public class LinkController extends BaseController {

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public DeleteResponse delete() throws SQLException {
        Integer id = request.getParaToInt("id");
        if (Objects.isNull(id) || id <= 0) {
            throw new ArgsException("id");
        }
        return new DeleteResponse(new Link().deleteById(id));
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public UpdateRecordResponse update() throws IOException, SQLException {
        UpdateLinkRequest linkRequest = getRequestBodyWithNullCheck(UpdateLinkRequest.class);
        new Link().set("linkName", linkRequest.getLinkName())
                .set("sort", linkRequest.getSort())
                .set("url", linkRequest.getUrl())
                .set("icon", linkRequest.getIcon())
                .set("alt", Objects.requireNonNullElse(linkRequest.getAlt(), ""))
                .updateById(linkRequest.getId());
        return new UpdateRecordResponse();
    }

    @ResponseBody
    public AdminApiPageDataStandardResponse<PageData<LinkDTO>> index() throws SQLException {
        return new AdminApiPageDataStandardResponse<>(new Link().find(ControllerUtil.unPageRequest()), "", request.getUri());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public UpdateRecordResponse add() throws IOException, SQLException {
        CreateLinkRequest linkRequest = getRequestBodyWithNullCheck(CreateLinkRequest.class);
        return new UpdateRecordResponse(new Link()
                .set("linkName", linkRequest.getLinkName())
                .set("sort", Objects.requireNonNullElse(linkRequest.getSort(), 0))
                .set("url", linkRequest.getUrl())
                .set("icon", linkRequest.getIcon())
                .set("alt", Objects.requireNonNullElse(linkRequest.getAlt(), ""))
                .save());
    }

}
