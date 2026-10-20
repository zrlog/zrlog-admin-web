package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.zrlog.admin.business.rest.request.CreateNavRequest;
import com.zrlog.admin.business.rest.request.UpdateNavRequest;
import com.zrlog.business.util.ControllerUtil;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.model.LogNav;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;

public class BlogNavService {
    private final jakarta.inject.Provider<LogNav> logNavProvider;

    @Inject
    public BlogNavService(jakarta.inject.Provider<LogNav> logNavProvider) {
        this.logNavProvider = logNavProvider;
    }

    public boolean delete(String ids) throws SQLException {
        List<Integer> navIds = parseIds(ids);
        if (navIds.isEmpty()) {
            return false;
        }
        String placeholders = placeholders(navIds.size());
        LogNav logNav = this.logNavProvider.get();
        Object count = logNav.queryFirstObj(
                "select count(1) from lognav where navId in (" + placeholders + ")", navIds.toArray());
        boolean allExist = count instanceof Number && ((Number) count).intValue() == navIds.size();
        return allExist && logNav.execute(
                "delete from lognav where navId in (" + placeholders + ")", navIds.toArray());
    }

    public PageData<LogNavDTO> find() throws SQLException {
        PageData<LogNavDTO> page = this.logNavProvider.get().find(ControllerUtil.unPageRequest());
        page.getRows().forEach(item -> item.setJumpUrl(item.getUrl()));
        return page;
    }

    public boolean add(CreateNavRequest request) throws SQLException {
        return this.logNavProvider
                .get()
                .set("navName", request.getNavName())
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("sort", request.getSort())
                .save();
    }

    public boolean update(UpdateNavRequest request) throws SQLException {
        return this.logNavProvider
                .get()
                .set("navName", request.getNavName())
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("sort", Objects.requireNonNullElse(request.getSort(), 0))
                .updateById(request.getId());
    }

    List<Integer> parseIds(String ids) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String id : ids.split(",")) {
            String value = id == null ? "" : id.trim();
            if (!value.isEmpty()) {
                result.add(Integer.parseInt(value));
            }
        }
        return new ArrayList<>(result);
    }

    private String placeholders(int size) {
        StringJoiner result = new StringJoiner(",");
        for (int i = 0; i < size; i++) {
            result.add("?");
        }
        return result.toString();
    }
}
