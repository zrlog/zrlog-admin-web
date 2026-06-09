package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.request.CreateNavRequest;
import com.zrlog.admin.business.rest.request.UpdateNavRequest;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.DeleteResponse;
import com.zrlog.admin.business.rest.response.UpdateRecordResponse;
import com.zrlog.admin.web.annotation.RefreshCache;
import com.zrlog.admin.web.annotation.RequestLock;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.controller.BaseController;
import com.zrlog.model.LogNav;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

public class BlogNavController extends BaseController {

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public DeleteResponse delete() throws SQLException {
        String idStr = getParamWithEmptyCheck("id");
        List<Integer> navIds = parseNavIds(idStr.split(","));
        if (navIds.isEmpty()) {
            return new DeleteResponse(false);
        }
        String placeholders = placeholders(navIds.size());
        LogNav logNav = new LogNav();
        Object matchedCount = logNav.queryFirstObj(
                "select count(1) from lognav where navId in (" + placeholders + ")",
                navIds.toArray());
        boolean allExists = matchedCount instanceof Number && ((Number) matchedCount).intValue() == navIds.size();
        boolean deleted = allExists && logNav.execute(
                "delete from lognav where navId in (" + placeholders + ")",
                navIds.toArray());
        return new DeleteResponse(deleted);
    }

    private List<Integer> parseNavIds(String[] ids) {
        Set<Integer> idSet = new LinkedHashSet<>();
        for (String id : ids) {
            if (Objects.isNull(id)) {
                continue;
            }
            String trimmed = id.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            idSet.add(Integer.parseInt(trimmed));
        }
        return new ArrayList<>(idSet);
    }

    private String placeholders(int size) {
        StringJoiner sj = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            sj.add("?");
        }
        return sj.toString();
    }

    @ResponseBody
    public AdminPageDataResponse<PageData<LogNavDTO>> index() throws SQLException {
        PageData<LogNavDTO> mapPageData = new LogNav().find(ControllerUtil.unPageRequest());
        mapPageData.getRows().forEach(e -> {
            e.setJumpUrl(e.getUrl());
        });
        return new AdminPageDataResponse<>(mapPageData, "", request.getUri());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public UpdateRecordResponse add() throws IOException, SQLException {
        CreateNavRequest createNavRequest = getRequestBodyWithNullCheck(CreateNavRequest.class);
        return new UpdateRecordResponse(new LogNav()
                .set("navName", createNavRequest.getNavName())
                .set("url", createNavRequest.getUrl())
                .set("icon", createNavRequest.getIcon())
                .set("sort", createNavRequest.getSort())
                .save());
    }

    @RefreshCache(async = true, updateStaticSites = StaticSiteType.BLOG)
    @ResponseBody
    @RequestLock
    public UpdateRecordResponse update() throws IOException, SQLException {
        UpdateNavRequest createNavRequest = getRequestBodyWithNullCheck(UpdateNavRequest.class);
        return new UpdateRecordResponse(new LogNav()
                .set("navName", createNavRequest.getNavName())
                .set("url", createNavRequest.getUrl())
                .set("icon", createNavRequest.getIcon())
                .set("sort", Objects.requireNonNullElse(createNavRequest.getSort(), 0))
                .updateById(createNavRequest.getId()));
    }

}
