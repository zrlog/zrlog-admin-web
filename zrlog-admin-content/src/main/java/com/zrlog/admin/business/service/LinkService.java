package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.rest.request.CreateLinkRequest;
import com.zrlog.admin.business.rest.request.UpdateLinkRequest;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.model.Link;

import java.sql.SQLException;
import java.util.Objects;

public class LinkService {

    public boolean delete(int id) throws SQLException {
        return new Link().deleteById(id);
    }

    public boolean update(UpdateLinkRequest request) throws SQLException {
        return new Link().set("linkName", request.getLinkName())
                .set("sort", request.getSort())
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("alt", Objects.requireNonNullElse(request.getAlt(), ""))
                .updateById(request.getId());
    }

    public PageData<LinkDTO> find(PageRequest pageRequest) throws SQLException {
        return new Link().find(pageRequest);
    }

    public boolean add(CreateLinkRequest request) throws SQLException {
        return new Link().set("linkName", request.getLinkName())
                .set("sort", Objects.requireNonNullElse(request.getSort(), 0))
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("alt", Objects.requireNonNullElse(request.getAlt(), ""))
                .save();
    }
}
